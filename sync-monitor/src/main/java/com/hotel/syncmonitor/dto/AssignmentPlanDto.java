package com.hotel.syncmonitor.dto;

import com.hotel.syncmonitor.domain.enums.RoomType;

import java.time.LocalDate;
import java.util.List;

/**
 * Output of the auto-assignment planner. Describes the proposed final state
 * for every reservation of a given room type, and which existing assignments
 * would be moved as a result of accepting the plan.
 *
 * <p>If {@code feasible} is false the planner could not place every reservation
 * (including the new one) using only pre-arrival reshuffles.</p>
 */
public record AssignmentPlanDto(
        boolean feasible,
        Long targetReservationId,
        RoomType roomType,
        List<Placement> placements,
        List<Placement> movedPlacements,
        String message
) {
    public record Placement(
            Long reservationId,
            String externalReservationId,
            String guestName,
            Long roomId,
            String roomNumber,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            // "NEW"      → newly assigned (the target reservation)
            // "MOVED"    → existing assignment moved to a different room
            // "UNCHANGED"→ existing assignment kept on the same room
            String change,
            // Previous room number, only set when change == "MOVED"
            String previousRoomNumber
    ) {}
}
