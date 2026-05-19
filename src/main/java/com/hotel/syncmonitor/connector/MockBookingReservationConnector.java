package com.hotel.syncmonitor.connector;

import com.hotel.syncmonitor.dto.BookingReservationDto;
import com.hotel.syncmonitor.domain.enums.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Simulates the Booking.com Connectivity API retrieve/acknowledge flow.
 * Real API access requires official partner authorization — this is mock-only.
 */
@Component
public class MockBookingReservationConnector implements BookingReservationConnector {

    private static final Logger log = LoggerFactory.getLogger(MockBookingReservationConnector.class);

    @Override
    public List<BookingReservationDto> fetchNewReservations() {
        return List.of(
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-001", "Guest A",
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 25),
                RoomType.DOUBLE, 2,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 14, 3, 0),
                LocalDateTime.of(2026, 5, 18, 14, 6, 0)
            ),
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-002", "Guest B",
                LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 26),
                RoomType.DOUBLE, 2,
                PaymentStatus.PAY_AT_PROPERTY, ReservationStatus.CONFIRMED, PmsSyncStatus.PENDING_IMPORT,
                LocalDateTime.of(2026, 5, 18, 15, 10, 0),
                null
            ),
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-003", "Guest C",
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 28),
                RoomType.FAMILY, 4,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 16, 0, 0),
                LocalDateTime.of(2026, 5, 18, 16, 4, 0)
            )
        );
    }

    @Override
    public void acknowledge(String externalReservationId) {
        log.info("Mock Booking.com acknowledge for reservation {}", externalReservationId);
    }
}
