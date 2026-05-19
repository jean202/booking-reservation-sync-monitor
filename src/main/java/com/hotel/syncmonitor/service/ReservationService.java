package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.connector.BookingReservationConnector;
import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.repository.ReservationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SyncLogService syncLogService;
    private final BookingReservationConnector connector;

    public ReservationService(ReservationRepository reservationRepository,
                              SyncLogService syncLogService,
                              BookingReservationConnector connector) {
        this.reservationRepository = reservationRepository;
        this.syncLogService = syncLogService;
        this.connector = connector;
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Reservation getReservation(Long reservationId) {
        return findReservationOrThrow(reservationId);
    }

    @Transactional
    public Reservation acknowledgeReservation(Long reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);

        connector.acknowledge(reservation.getExternalReservationId());
        reservation.setPmsSyncStatus(PmsSyncStatus.ACKNOWLEDGED);
        Reservation saved = reservationRepository.save(reservation);

        syncLogService.record(reservation.getChannel(), reservation.getExternalReservationId(),
                "ACKNOWLEDGED", "Reservation acknowledged in PMS.");

        return saved;
    }

    private Reservation findReservationOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reservation not found: " + reservationId));
    }
}
