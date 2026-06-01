package com.hotel.syncmonitor.repository;

import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSyncLogRepository extends JpaRepository<ReservationSyncLog, Long> {

    List<ReservationSyncLog> findByExternalReservationIdOrderByCreatedAtDesc(String externalReservationId);
}
