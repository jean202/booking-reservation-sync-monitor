package com.hotel.syncmonitor;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.BookingReservationDto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mock-only test data builders. */
public final class Fixtures {

    private Fixtures() {}

    public static Reservation reservation(String externalId, RoomType type, int guests,
                                          LocalDate checkIn, LocalDate checkOut) {
        Reservation r = new Reservation();
        r.setChannel(OtaChannel.BOOKING_COM);
        r.setExternalReservationId(externalId);
        r.setGuestName("Guest " + externalId);
        r.setCheckInDate(checkIn);
        r.setCheckOutDate(checkOut);
        r.setRoomType(type);
        r.setGuestCount(guests);
        r.setPaymentStatus(PaymentStatus.PREPAID);
        r.setReservationStatus(ReservationStatus.CONFIRMED);
        r.setPmsSyncStatus(PmsSyncStatus.IMPORTED_TO_PMS);
        return r;
    }

    public static Reservation doubleReservation(String externalId, LocalDate checkIn, LocalDate checkOut) {
        return reservation(externalId, RoomType.DOUBLE, 2, checkIn, checkOut);
    }

    public static Room room(String number, RoomType type, int capacity) {
        Room room = new Room();
        room.setRoomNumber(number);
        room.setRoomType(type);
        room.setCapacity(capacity);
        room.setStatus(RoomStatus.ACTIVE);
        return room;
    }

    public static Room doubleRoom(String number) {
        return room(number, RoomType.DOUBLE, 2);
    }

    public static BookingReservationDto dto(OtaChannel channel, String externalId,
                                            ReservationStatus status, PmsSyncStatus pms,
                                            LocalDateTime pmsImportedAt) {
        return new BookingReservationDto(
                channel, externalId, "Guest " + externalId,
                LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 25),
                RoomType.DOUBLE, 2,
                PaymentStatus.PREPAID, status, pms,
                LocalDateTime.of(2026, 5, 18, 14, 3),
                pmsImportedAt);
    }

    public static BookingReservationDto dto(String externalId) {
        return dto(OtaChannel.BOOKING_COM, externalId, ReservationStatus.CONFIRMED,
                PmsSyncStatus.IMPORTED_TO_PMS, LocalDateTime.of(2026, 5, 18, 14, 6));
    }
}
