package com.hotel.shifthelper.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A reservation placed in a specific room for an interval. The interval may
 * differ from the reservation's own dates only via planner reshuffles
 * (in this domain, the planner keeps them aligned).
 */
public final class Assignment {

    private final Long id;
    private final Long reservationId;
    private Long roomId;
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    public Assignment(Long id, Long reservationId, Long roomId,
                      LocalDate checkIn, LocalDate checkOut) {
        if (reservationId == null) throw new IllegalArgumentException("reservationId required");
        if (roomId == null) throw new IllegalArgumentException("roomId required");
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("interval required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
        this.id = id;
        this.reservationId = reservationId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public Long id() { return id; }
    public Long reservationId() { return reservationId; }
    public Long roomId() { return roomId; }
    public LocalDate checkIn() { return checkIn; }
    public LocalDate checkOut() { return checkOut; }

    public void moveTo(Long newRoomId) {
        if (newRoomId == null) throw new IllegalArgumentException("newRoomId required");
        this.roomId = newRoomId;
    }

    public boolean overlaps(LocalDate otherIn, LocalDate otherOut) {
        return checkIn.isBefore(otherOut) && checkOut.isAfter(otherIn);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Assignment a && Objects.equals(id, a.id);
    }
    @Override
    public int hashCode() { return Objects.hash(id); }
}
