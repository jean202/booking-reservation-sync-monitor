package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.enums.OtaChannel;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SyncLogService {

    private final ReservationSyncLogRepository syncLogRepository;

    public SyncLogService(ReservationSyncLogRepository syncLogRepository) {
        this.syncLogRepository = syncLogRepository;
    }

    public void record(OtaChannel channel, String externalReservationId, String eventType, String message) {
        ReservationSyncLog log = new ReservationSyncLog();
        log.setChannel(channel);
        log.setExternalReservationId(externalReservationId);
        log.setEventType(eventType);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        syncLogRepository.save(log);
    }
}
