package com.hotel.syncmonitor.controller;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import com.hotel.syncmonitor.dto.AssignmentPlanDto;
import com.hotel.syncmonitor.dto.RoomCandidateDto;
import com.hotel.syncmonitor.service.AutoAssignmentService;
import com.hotel.syncmonitor.service.ReservationService;
import com.hotel.syncmonitor.service.RoomAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.hotel.syncmonitor.Fixtures.doubleReservation;
import static com.hotel.syncmonitor.Fixtures.doubleRoom;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired private MockMvc mvc;
    @MockBean private ReservationService reservationService;
    @MockBean private RoomAssignmentService roomAssignmentService;
    @MockBean private AutoAssignmentService autoAssignmentService;

    private static final LocalDate IN = LocalDate.of(2026, 5, 23);
    private static final LocalDate OUT = LocalDate.of(2026, 5, 25);

    private static Reservation withId(Reservation r, long id) {
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    @Test
    void listIncludesAssignedRoomNumberWhenPresent() throws Exception {
        given(reservationService.getAllReservations()).willReturn(List.of(
                withId(doubleReservation("BKG-1", IN, OUT), 1L),
                withId(doubleReservation("BKG-2", IN, OUT), 2L)));
        given(roomAssignmentService.getAssignedRoomNumbersByReservationId()).willReturn(Map.of(1L, "201"));

        mvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].externalReservationId").value("BKG-1"))
                .andExpect(jsonPath("$[0].assignedRoomNumber").value("201"))
                .andExpect(jsonPath("$[0].checkInDate").value("2026-05-23"))
                .andExpect(jsonPath("$[1].assignedRoomNumber").doesNotExist());
    }

    @Test
    void getByIdReturnsReservation() throws Exception {
        given(reservationService.getReservation(1L)).willReturn(withId(doubleReservation("BKG-1", IN, OUT), 1L));
        given(roomAssignmentService.getAssignedRoomNumbersByReservationId()).willReturn(Map.of());

        mvc.perform(get("/api/reservations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roomType").value("DOUBLE"))
                .andExpect(jsonPath("$.pmsSyncStatus").value("IMPORTED_TO_PMS"));
    }

    @Test
    void missingReservationReturns404WithErrorBody() throws Exception {
        given(roomAssignmentService.getAssignedRoomNumbersByReservationId()).willReturn(Map.of());
        given(reservationService.getReservation(9L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found: 9"));

        mvc.perform(get("/api/reservations/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Reservation not found: 9"))
                .andExpect(jsonPath("$.path").value("/api/reservations/9"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void roomCandidatesAreReturned() throws Exception {
        given(roomAssignmentService.getAvailableRooms(1L))
                .willReturn(List.of(new RoomCandidateDto(5L, "201", RoomType.DOUBLE, 2)));

        mvc.perform(get("/api/reservations/1/room-candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomNumber").value("201"))
                .andExpect(jsonPath("$[0].capacity").value(2));
    }

    @Test
    void assignRoomReturnsAssignment() throws Exception {
        Reservation r = withId(doubleReservation("BKG-1", IN, OUT), 1L);
        Room room = doubleRoom("201");
        ReflectionTestUtils.setField(room, "id", 5L);
        RoomAssignment a = new RoomAssignment();
        ReflectionTestUtils.setField(a, "id", 7L);
        a.setReservation(r);
        a.setRoom(room);
        a.setAssignedCheckIn(IN);
        a.setAssignedCheckOut(OUT);
        a.setAssignmentStatus(AssignmentStatus.ASSIGNED);
        given(roomAssignmentService.assignRoom(1L, 5L)).willReturn(a);

        mvc.perform(post("/api/reservations/1/assign-room/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.reservationId").value(1))
                .andExpect(jsonPath("$.roomNumber").value("201"))
                .andExpect(jsonPath("$.assignmentStatus").value("ASSIGNED"));
    }

    @Test
    void assignRoomConflictReturns409() throws Exception {
        given(roomAssignmentService.assignRoom(eq(1L), any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Room 201 is already assigned for this date range."));

        mvc.perform(post("/api/reservations/1/assign-room/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void assignRoomBadRequestReturns400() throws Exception {
        given(roomAssignmentService.assignRoom(eq(1L), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad room"));

        mvc.perform(post("/api/reservations/1/assign-room/5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("bad room"));
    }

    @Test
    void nonNumericIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/reservations/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void previewAndCommitReturnPlan() throws Exception {
        AssignmentPlanDto plan = new AssignmentPlanDto(true, 1L, RoomType.DOUBLE, List.of(), List.of(), "ok");
        given(autoAssignmentService.preview(1L)).willReturn(plan);
        given(autoAssignmentService.commit(1L)).willReturn(plan);

        mvc.perform(post("/api/reservations/1/auto-assign/preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.targetReservationId").value(1));
        mvc.perform(post("/api/reservations/1/auto-assign/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("ok"));
    }

    @Test
    void infeasibleCommitReturns409() throws Exception {
        given(autoAssignmentService.commit(1L))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "No feasible placement"));

        mvc.perform(post("/api/reservations/1/auto-assign/commit"))
                .andExpect(status().isConflict());
    }

    @Test
    void acknowledgeReturnsUpdatedReservation() throws Exception {
        Reservation r = withId(doubleReservation("BKG-1", IN, OUT), 1L);
        r.setPmsSyncStatus(PmsSyncStatus.ACKNOWLEDGED);
        given(reservationService.acknowledgeReservation(1L)).willReturn(r);

        mvc.perform(post("/api/reservations/1/acknowledge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pmsSyncStatus").value("ACKNOWLEDGED"));
    }

    @Test
    void corsAllowsLocalhostOrigins() throws Exception {
        mvc.perform(options("/api/reservations")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsRejectsForeignOrigins() throws Exception {
        mvc.perform(options("/api/reservations")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
