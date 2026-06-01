package com.hotel.shifthelper.domain.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Planner output: feasibility + per-reservation placements after the
 * target reservation is considered. Returned by the domain planner and
 * surfaced to the UI as a preview before commit.
 */
public record AssignmentPlan(
        boolean feasible,
        Long targetReservationId,
        List<Placement> placements,
        List<Placement> movedPlacements,
        String message
) {
    public enum Change { NEW, MOVED, UNCHANGED, LOCKED }

    public record Placement(
            Long reservationId,
            String guestName,
            Long roomId,
            String roomNumber,
            LocalDate checkIn,
            LocalDate checkOut,
            Change change,
            String previousRoomNumber
    ) {}

    public static AssignmentPlan infeasible(Long targetId, String reason) {
        return new AssignmentPlan(false, targetId, List.of(), List.of(), reason);
    }
}
