package com.hotel.syncmonitor.repository;

import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, Long> {

    List<RoomAssignment> findByReservationIdAndAssignmentStatus(Long reservationId, AssignmentStatus status);

    @Query("""
            SELECT ra.reservation.id, ra.room.roomNumber
            FROM RoomAssignment ra
            WHERE ra.assignmentStatus = 'ASSIGNED'
            """)
    List<Object[]> findAssignedReservationIdAndRoomNumberPairs();

    // Prevents assigning a room when stay dates intersect; back-to-back checkout/check-in dates do not overlap.
    @Query("""
            SELECT ra FROM RoomAssignment ra
            WHERE ra.room.id = :roomId
              AND ra.assignmentStatus = 'ASSIGNED'
              AND ra.assignedCheckIn < :checkOut
              AND ra.assignedCheckOut > :checkIn
            """)
    List<RoomAssignment> findOverlapping(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}
