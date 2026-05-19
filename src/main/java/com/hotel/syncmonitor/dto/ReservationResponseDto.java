package com.hotel.syncmonitor.dto;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.enums.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReservationResponseDto(
        Long id,
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
        LocalDateTime pmsImportedAt,
        String assignedRoomNumber
) {
    public static ReservationResponseDto from(Reservation reservation) {
        return from(reservation, null);
    }

    public static ReservationResponseDto from(Reservation reservation, String assignedRoomNumber) {
        return new ReservationResponseDto(
                reservation.getId(),
                reservation.getChannel(),
                reservation.getExternalReservationId(),
                reservation.getGuestName(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getRoomType(),
                reservation.getGuestCount(),
                reservation.getPaymentStatus(),
                reservation.getReservationStatus(),
                reservation.getPmsSyncStatus(),
                reservation.getEmailReceivedAt(),
                reservation.getPmsImportedAt(),
                assignedRoomNumber
        );
    }
}
