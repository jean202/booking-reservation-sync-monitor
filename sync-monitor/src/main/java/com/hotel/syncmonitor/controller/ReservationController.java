package com.hotel.syncmonitor.controller;

import com.hotel.syncmonitor.dto.AssignmentPlanDto;
import com.hotel.syncmonitor.dto.ReservationResponseDto;
import com.hotel.syncmonitor.dto.RoomCandidateDto;
import com.hotel.syncmonitor.dto.RoomAssignmentResponseDto;
import com.hotel.syncmonitor.service.AutoAssignmentService;
import com.hotel.syncmonitor.service.ReservationService;
import com.hotel.syncmonitor.service.RoomAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Reservation management and room assignment")
public class ReservationController {

    private final ReservationService reservationService;
    private final RoomAssignmentService roomAssignmentService;
    private final AutoAssignmentService autoAssignmentService;

    public ReservationController(ReservationService reservationService,
                                  RoomAssignmentService roomAssignmentService,
                                  AutoAssignmentService autoAssignmentService) {
        this.reservationService = reservationService;
        this.roomAssignmentService = roomAssignmentService;
        this.autoAssignmentService = autoAssignmentService;
    }

    @GetMapping
    @Operation(summary = "List all reservations")
    public List<ReservationResponseDto> getAllReservations() {
        var assignedRoomByReservationId = roomAssignmentService.getAssignedRoomNumbersByReservationId();
        return reservationService.getAllReservations()
                .stream()
                .map(r -> ReservationResponseDto.from(r, assignedRoomByReservationId.get(r.getId())))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get reservation by ID")
    public ReservationResponseDto getReservation(@PathVariable Long id) {
        var assignedRoomByReservationId = roomAssignmentService.getAssignedRoomNumbersByReservationId();
        var reservation = reservationService.getReservation(id);
        return ReservationResponseDto.from(reservation, assignedRoomByReservationId.get(reservation.getId()));
    }

    @GetMapping("/{id}/room-candidates")
    @Operation(summary = "Get available room candidates",
               description = "Returns ACTIVE rooms matching room type and capacity with no overlapping assignments.")
    public List<RoomCandidateDto> getRoomCandidates(@PathVariable Long id) {
        return roomAssignmentService.getAvailableRooms(id);
    }

    @PostMapping("/{id}/assign-room/{roomId}")
    @Operation(summary = "Assign a room to a reservation",
               description = "Creates a room assignment. Returns 400 for invalid room type/capacity/status and 409 for overlap.")
    public ResponseEntity<RoomAssignmentResponseDto> assignRoom(@PathVariable Long id, @PathVariable Long roomId) {
        return ResponseEntity.ok(RoomAssignmentResponseDto.from(roomAssignmentService.assignRoom(id, roomId)));
    }

    @PostMapping("/{id}/auto-assign/preview")
    @Operation(summary = "Preview auto-assignment with reshuffle",
               description = "Computes a feasible assignment plan that may move pre-arrival reservations of the same room type. Does not persist.")
    public AssignmentPlanDto previewAutoAssign(@PathVariable Long id) {
        return autoAssignmentService.preview(id);
    }

    @PostMapping("/{id}/auto-assign/commit")
    @Operation(summary = "Commit auto-assignment with reshuffle",
               description = "Persists the assignment plan, moving pre-arrival assignments as needed. Returns 409 if not feasible.")
    public AssignmentPlanDto commitAutoAssign(@PathVariable Long id) {
        return autoAssignmentService.commit(id);
    }

    @PostMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge a reservation",
               description = "Calls the connector acknowledge hook, sets pmsSyncStatus to ACKNOWLEDGED, and writes a sync log.")
    public ResponseEntity<ReservationResponseDto> acknowledgeReservation(@PathVariable Long id) {
        return ResponseEntity.ok(ReservationResponseDto.from(reservationService.acknowledgeReservation(id)));
    }
}
