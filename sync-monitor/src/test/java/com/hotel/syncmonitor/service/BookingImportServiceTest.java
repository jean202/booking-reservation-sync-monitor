package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.connector.BookingReservationConnector;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.BookingReservationDto;
import com.hotel.syncmonitor.dto.ImportResultDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class BookingImportServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    private BookingImportService bookingImportService;

    @BeforeEach
    void setUp() {
        BookingReservationConnector connector = new FixedBookingConnector(List.of(reservationDto()));
        SyncLogService syncLogService = new SyncLogService(syncLogRepository);
        bookingImportService = new BookingImportService(connector, reservationRepository, syncLogService);
    }

    @Test
    void importReservationsDoesNotCreateDuplicateRowsForSameChannelAndExternalId() {
        ImportResultDto firstImport = bookingImportService.importReservations();
        ImportResultDto secondImport = bookingImportService.importReservations();

        assertThat(firstImport.imported()).isEqualTo(1);
        assertThat(secondImport.skipped()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findByChannelAndExternalReservationId(
                OtaChannel.BOOKING_COM, "BKG-001"
        )).isPresent();
    }

    private BookingReservationDto reservationDto() {
        return new BookingReservationDto(
                OtaChannel.BOOKING_COM,
                "BKG-001",
                "Guest A",
                LocalDate.of(2026, 5, 23),
                LocalDate.of(2026, 5, 25),
                RoomType.DOUBLE,
                2,
                PaymentStatus.PREPAID,
                ReservationStatus.CONFIRMED,
                PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 14, 3),
                LocalDateTime.of(2026, 5, 18, 14, 6)
        );
    }

    private record FixedBookingConnector(List<BookingReservationDto> reservations)
            implements BookingReservationConnector {

        @Override
        public List<BookingReservationDto> fetchNewReservations() {
            return reservations;
        }

        @Override
        public void acknowledge(String externalReservationId) {
        }
    }
}
