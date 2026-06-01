package com.hotel.shifthelper.domain.port;

import com.hotel.shifthelper.domain.model.Assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository {
    Optional<Assignment> findById(Long id);

    /** All assignments whose interval intersects today or the future. */
    List<Assignment> findActiveOn(LocalDate referenceDate);

    Optional<Assignment> findByReservationId(Long reservationId);

    Assignment save(Assignment assignment);
}
