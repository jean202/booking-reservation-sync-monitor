package com.hotel.shifthelper.domain.port;

import com.hotel.shifthelper.domain.model.Reservation;

import java.util.Optional;

public interface ReservationRepository {
    Optional<Reservation> findById(Long id);
    Reservation save(Reservation reservation);
}
