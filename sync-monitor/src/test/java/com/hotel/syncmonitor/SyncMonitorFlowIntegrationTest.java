package com.hotel.syncmonitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end over the mock connector and seeded rooms (no external calls). */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SyncMonitorFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RoomRepository roomRepository;

    @Test
    void seededRoomsExistOnStartup() {
        assertThat(roomRepository.count()).isEqualTo(10);
    }

    @Test
    void importIsIdempotentAcrossCalls() throws Exception {
        mvc.perform(post("/api/mock/booking/reservations/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(8));
        mvc.perform(post("/api/mock/booking/reservations/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.skipped").value(8));
        mvc.perform(get("/api/reservations")).andExpect(jsonPath("$.length()").value(8));
    }

    @Test
    void importAssignAndDashboardFlow() throws Exception {
        mvc.perform(post("/api/mock/booking/reservations/import")).andExpect(status().isOk());

        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.totalReservationCount").value(8))
                .andExpect(jsonPath("$.unassignedReservationCount").value(8))
                .andExpect(jsonPath("$.notImportedToPmsCount").value(1));

        long bkg001 = idOf("BKG-001");
        JsonNode candidates = json(get("/api/reservations/" + bkg001 + "/room-candidates"));
        assertThat(candidates).isNotEmpty();
        long roomId = candidates.get(0).get("id").asLong();

        mvc.perform(post("/api/reservations/" + bkg001 + "/assign-room/" + roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"));
        mvc.perform(get("/api/reservations/" + bkg001))
                .andExpect(jsonPath("$.assignedRoomNumber").value(candidates.get(0).get("roomNumber").asText()));
        mvc.perform(get("/api/dashboard"))
                .andExpect(jsonPath("$.unassignedReservationCount").value(7));
    }

    @Test
    void pendingImportReservationCannotBeAssigned() throws Exception {
        mvc.perform(post("/api/mock/booking/reservations/import")).andExpect(status().isOk());
        long bkg007 = idOf("BKG-007");
        long twinRoom = json(get("/api/reservations/" + bkg007 + "/room-candidates")).get(0).get("id").asLong();

        mvc.perform(post("/api/reservations/" + bkg007 + "/assign-room/" + twinRoom))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Room can only be assigned after PMS import. Current status: PENDING_IMPORT"));
        mvc.perform(post("/api/reservations/" + bkg007 + "/auto-assign/preview"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownReservationReturns404() throws Exception {
        mvc.perform(get("/api/reservations/12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Reservation not found: 12345"));
    }

    private long idOf(String externalId) throws Exception {
        for (JsonNode n : json(get("/api/reservations"))) {
            if (n.get("externalReservationId").asText().equals(externalId)) return n.get("id").asLong();
        }
        throw new AssertionError("missing " + externalId);
    }

    private JsonNode json(org.springframework.test.web.servlet.RequestBuilder req) throws Exception {
        return objectMapper.readTree(mvc.perform(req).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
