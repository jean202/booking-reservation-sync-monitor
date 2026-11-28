package com.hotel.syncmonitor.repository;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.enums.OtaChannel;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByChannelAndExternalReservationId(OtaChannel channel, String externalReservationId);

    List<Reservation> findByReservationStatus(ReservationStatus status);
    List<Reservation> findByReservationStatusNot(ReservationStatus status);

    List<Reservation> findByPmsSyncStatus(PmsSyncStatus pmsSyncStatus);

    long countByPmsSyncStatus(PmsSyncStatus pmsSyncStatus);
}
