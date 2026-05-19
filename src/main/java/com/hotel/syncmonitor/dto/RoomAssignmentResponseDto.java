package com.hotel.syncmonitor.dto;

import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;

import java.time.LocalDate;

public record RoomAssignmentResponseDto(
        Long id,
        Long reservationId,
        Long roomId,
        String roomNumber,
        RoomType roomType,
        LocalDate assignedCheckIn,
        LocalDate assignedCheckOut,
        AssignmentStatus assignmentStatus
) {
    public static RoomAssignmentResponseDto from(RoomAssignment assignment) {
        return new RoomAssignmentResponseDto(
                assignment.getId(),
                assignment.getReservation().getId(),
                assignment.getRoom().getId(),
                assignment.getRoom().getRoomNumber(),
                assignment.getRoom().getRoomType(),
                assignment.getAssignedCheckIn(),
                assignment.getAssignedCheckOut(),
                assignment.getAssignmentStatus()
        );
    }
}
