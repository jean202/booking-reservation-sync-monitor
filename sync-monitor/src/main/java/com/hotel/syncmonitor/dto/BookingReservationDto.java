package com.hotel.syncmonitor.dto;

import com.hotel.syncmonitor.domain.enums.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BookingReservationDto(
        OtaChannel channel,
        String externalReservationId,
        String guestName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        RoomType roomType,
        int guestCount,
        PaymentStatus paymentStatus,
        ReservationStatus reservationStatus,
        PmsSyncStatus pmsSyncStatus,
        LocalDateTime emailReceivedAt,
        LocalDateTime pmsImportedAt
) {}
