package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.connector.BookingReservationConnector;
import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.dto.BookingReservationDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.hotel.syncmonitor.Fixtures.doubleReservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
class ReservationServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    private final List<String> acknowledged = new ArrayList<>();
    private ReservationService service;

    @BeforeEach
    void setUp() {
        BookingReservationConnector connector = new BookingReservationConnector() {
            @Override
            public List<BookingReservationDto> fetchNewReservations() {
                return List.of();
            }

            @Override
            public void acknowledge(String externalReservationId) {
                acknowledged.add(externalReservationId);
            }
        };
        service = new ReservationService(reservationRepository, new SyncLogService(syncLogRepository), connector);
    }

    @Test
    void getAllReturnsEveryReservation() {
        reservationRepository.save(doubleReservation("BKG-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)));
        reservationRepository.save(doubleReservation("BKG-2", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)));

        assertThat(service.getAllReservations()).extracting(Reservation::getExternalReservationId)
                .containsExactlyInAnyOrder("BKG-1", "BKG-2");
    }

    @Test
    void getReservationReturnsById() {
        Reservation saved = reservationRepository.save(
                doubleReservation("BKG-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)));

        assertThat(service.getReservation(saved.getId()).getExternalReservationId()).isEqualTo("BKG-1");
    }

    @Test
    void unknownReservationIsNotFound() {
        assertThatThrownBy(() -> service.getReservation(999L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void acknowledgeNotifiesChannelUpdatesStatusAndLogs() {
        Reservation saved = reservationRepository.save(
                doubleReservation("BKG-1", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2)));

        Reservation result = service.acknowledgeReservation(saved.getId());

        assertThat(result.getPmsSyncStatus()).isEqualTo(PmsSyncStatus.ACKNOWLEDGED);
        assertThat(reservationRepository.findById(saved.getId()).orElseThrow().getPmsSyncStatus())
                .isEqualTo(PmsSyncStatus.ACKNOWLEDGED);
        assertThat(acknowledged).containsExactly("BKG-1");
        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-1"))
                .extracting(ReservationSyncLog::getEventType).containsExactly("ACKNOWLEDGED");
    }

    @Test
    void acknowledgeUnknownReservationDoesNotCallChannel() {
        assertThatThrownBy(() -> service.acknowledgeReservation(999L))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(acknowledged).isEmpty();
    }
}
