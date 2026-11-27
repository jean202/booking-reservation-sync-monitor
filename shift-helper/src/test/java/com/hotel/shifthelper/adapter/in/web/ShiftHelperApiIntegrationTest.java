package com.hotel.shifthelper.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // shared in-memory H2 (DB_CLOSE_DELAY=-1, ddl-auto=update) would leak rows between tests
class ShiftHelperApiIntegrationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 6, 2);

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Asia/Seoul");
            return Clock.fixed(TODAY.atStartOfDay(zone).toInstant(), zone);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper om;

    @Test
    void registerListAndToggleRooms() throws Exception {
        long id = registerRoom("101", 2);
        registerRoom("102", 4);

        mvc.perform(get("/api/shift/rooms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].inService").value(true));

        mvc.perform(patch("/api/shift/rooms/" + id + "/service")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inService\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inService").value(false))
                .andExpect(jsonPath("$.number").value("101"));
    }

    @Test
    void invalidRoomIsBadRequestWithMessage() throws Exception {
        mvc.perform(post("/api/shift/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"101\",\"capacity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Capacity must be positive."));
    }

    @Test
    void toggleUnknownRoomIs404() throws Exception {
        mvc.perform(patch("/api/shift/rooms/999/service")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inService\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidReservationIsBadRequest() throws Exception {
        mvc.perform(post("/api/shift/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestName\":\"Kim\",\"guestCount\":2,\"checkIn\":\"2026-06-05\",\"checkOut\":\"2026-06-05\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Check-out must be after check-in."));
        mvc.perform(post("/api/shift/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestCount\":2,\"checkIn\":\"2026-06-05\",\"checkOut\":\"2026-06-06\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void previewThenCommitAssignsAndListsActiveAssignment() throws Exception {
        long room = registerRoom("101", 2);
        long res = createReservation("Kim", 2, "2026-06-05", "2026-06-07");

        mvc.perform(post("/api/shift/reservations/" + res + "/assignment/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.placements[0].change").value("NEW"))
                .andExpect(jsonPath("$.placements[0].roomNumber").value("101"));
        mvc.perform(get("/api/shift/assignments")).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/shift/reservations/" + res + "/assignment/commit"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/shift/assignments"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].reservationId").value(res))
                .andExpect(jsonPath("$[0].roomId").value(room))
                .andExpect(jsonPath("$[0].checkIn").value("2026-06-05"));
        mvc.perform(post("/api/shift/reservations/" + res + "/assignment/commit"))
                .andExpect(status().isConflict());
    }

    @Test
    void reshuffleThroughApiMovesSmallerParty() throws Exception {
        registerRoom("101", 2);
        long large = registerRoom("102", 4);
        long mid = registerRoom("103", 3);
        long kim = createReservation("Kim", 3, "2026-06-06", "2026-06-08");
        long lee = createReservation("Lee", 4, "2026-06-06", "2026-06-08");
        // Kim takes 103 (best fit) first; force him into 102 by taking 103 out of service.
        mvc.perform(patch("/api/shift/rooms/" + mid + "/service")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inService\":false}"));
        mvc.perform(post("/api/shift/reservations/" + kim + "/assignment/commit")).andExpect(status().isOk());
        mvc.perform(patch("/api/shift/rooms/" + mid + "/service")
                .contentType(MediaType.APPLICATION_JSON).content("{\"inService\":true}"));

        mvc.perform(post("/api/shift/reservations/" + lee + "/assignment/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedPlacements[0].reservationId").value(kim))
                .andExpect(jsonPath("$.movedPlacements[0].previousRoomNumber").value("102"));

        JsonNode list = om.readTree(mvc.perform(get("/api/shift/assignments")).andReturn().getResponse().getContentAsString());
        for (JsonNode a : list) {
            if (a.get("reservationId").asLong() == kim) assertThat(a.get("roomId").asLong()).isEqualTo(mid);
            if (a.get("reservationId").asLong() == lee) assertThat(a.get("roomId").asLong()).isEqualTo(large);
        }
    }

    @Test
    void infeasibleCommitIs409() throws Exception {
        long res = createReservation("Kim", 5, "2026-06-05", "2026-06-07");
        registerRoom("101", 2);

        mvc.perform(post("/api/shift/reservations/" + res + "/assignment/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(false));
        mvc.perform(post("/api/shift/reservations/" + res + "/assignment/commit"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownReservationIs404() throws Exception {
        mvc.perform(post("/api/shift/reservations/999/assignment/preview"))
                .andExpect(status().isNotFound());
    }

    @Disabled("FINDINGS #8: 중복 객실 번호 등록이 4xx가 아니라 500으로 응답한다")
    @Test
    void duplicateRoomNumberIsClientError() throws Exception {
        registerRoom("101", 2);

        mvc.perform(post("/api/shift/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"101\",\"capacity\":2}"))
                .andExpect(status().is4xxClientError());
    }

    private long registerRoom(String number, int capacity) throws Exception {
        return idOf(mvc.perform(post("/api/shift/rooms").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"" + number + "\",\"capacity\":" + capacity + "}"))
                .andExpect(status().isOk()));
    }

    private long createReservation(String guest, int count, String in, String out) throws Exception {
        return idOf(mvc.perform(post("/api/shift/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestName\":\"" + guest + "\",\"guestCount\":" + count
                                + ",\"checkIn\":\"" + in + "\",\"checkOut\":\"" + out + "\"}"))
                .andExpect(status().isOk()));
    }

    private long idOf(ResultActions result) throws Exception {
        return om.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
}
