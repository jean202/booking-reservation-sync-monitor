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
        // Demo scenario:
        //   * 4 DOUBLE reservations on 3 DOUBLE rooms (201/202/203) → triggers reshuffle.
        //   * One PENDING_IMPORT (BKG-007) demonstrates "PMS-import-required" validation.
        //   * One per-room-type (SINGLE/TWIN/FAMILY/SUITE) covers all type-specific assign paths.
        return List.of(
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-001", "Guest A",
                LocalDate.of(2026, 5, 22), LocalDate.of(2026, 5, 24),
                RoomType.DOUBLE, 2,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 17, 14, 3, 0),
                LocalDateTime.of(2026, 5, 17, 14, 6, 0)
            ),
            new BookingReservationDto(
                OtaChannel.AGODA, "BKG-002", "Guest B",
                LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 26),
                RoomType.DOUBLE, 2,
                PaymentStatus.PAY_AT_PROPERTY, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 17, 15, 10, 0),
                LocalDateTime.of(2026, 5, 17, 15, 13, 0)
            ),
            new BookingReservationDto(
                OtaChannel.TRIP_COM, "BKG-003", "Guest C",
                LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 28),
                RoomType.DOUBLE, 2,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 17, 16, 0, 0),
                LocalDateTime.of(2026, 5, 17, 16, 4, 0)
            ),
            // BKG-004: 4-night DOUBLE spanning others. Used as the reshuffle trigger.
            new BookingReservationDto(
                OtaChannel.AIRBNB, "BKG-004", "Guest D",
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 27),
                RoomType.DOUBLE, 2,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 9, 12, 0),
                LocalDateTime.of(2026, 5, 18, 9, 18, 0)
            ),
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-005", "Guest E",
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 24),
                RoomType.SINGLE, 1,
                PaymentStatus.PAY_AT_PROPERTY, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 10, 30, 0),
                LocalDateTime.of(2026, 5, 18, 10, 32, 0)
            ),
            new BookingReservationDto(
                OtaChannel.BOOKING_COM, "BKG-006", "Guest F (Family)",
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 28),
                RoomType.FAMILY, 4,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 11, 45, 0),
                LocalDateTime.of(2026, 5, 18, 11, 50, 0)
            ),
            // BKG-007: PENDING_IMPORT → cannot be auto-assigned (validation demo).
            new BookingReservationDto(
                OtaChannel.AGODA, "BKG-007", "Guest G",
                LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 27),
                RoomType.TWIN, 2,
                PaymentStatus.PAY_AT_PROPERTY, ReservationStatus.CONFIRMED, PmsSyncStatus.PENDING_IMPORT,
                LocalDateTime.of(2026, 5, 18, 13, 5, 0),
                null
            ),
            new BookingReservationDto(
                OtaChannel.TRIP_COM, "BKG-008", "Guest H (Suite)",
                LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 29),
                RoomType.SUITE, 3,
                PaymentStatus.PREPAID, ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 18, 30, 0),
                LocalDateTime.of(2026, 5, 18, 18, 34, 0)
            )
        );
    }

    @Override
    public void acknowledge(String externalReservationId) {
        log.info("Mock Booking.com acknowledge for reservation {}", externalReservationId);
    }
}
