package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.enums.OtaChannel;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class SyncLogServiceTest {

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    @Test
    void recordPersistsEventWithTimestamp() {
        SyncLogService service = new SyncLogService(syncLogRepository);
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        service.record(OtaChannel.AGODA, "BKG-009", "IMPORTED", "hello");

        List<ReservationSyncLog> logs = syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-009");
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getChannel()).isEqualTo(OtaChannel.AGODA);
            assertThat(log.getEventType()).isEqualTo("IMPORTED");
            assertThat(log.getMessage()).isEqualTo("hello");
            assertThat(log.getCreatedAt()).isAfter(before);
        });
    }

    @Test
    void logsAreFilteredByExternalReservationId() {
        SyncLogService service = new SyncLogService(syncLogRepository);
        service.record(OtaChannel.AGODA, "BKG-A", "IMPORTED", "a");
        service.record(OtaChannel.AGODA, "BKG-B", "IMPORTED", "b");

        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-A"))
                .extracting(ReservationSyncLog::getMessage).containsExactly("a");
    }
}
