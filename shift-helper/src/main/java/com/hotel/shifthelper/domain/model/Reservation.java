package com.hotel.shifthelper.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single-form reservation entered manually by the shift worker.
 * Same-day check-in is allowed; check-out must be after check-in.
 */
public final class Reservation {

    private final Long id;
    private final String guestName;
    private final int guestCount;
    private final LocalDate checkIn;
    private final LocalDate checkOut;

    public Reservation(Long id, String guestName, int guestCount,
                       LocalDate checkIn, LocalDate checkOut) {
        if (guestName == null || guestName.isBlank()) {
            throw new IllegalArgumentException("Guest name is required.");
        }
        if (guestCount <= 0) {
            throw new IllegalArgumentException("Guest count must be positive.");
        }
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and check-out are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("Check-out must be after check-in.");
        }
        this.id = id;
        this.guestName = guestName;
        this.guestCount = guestCount;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public Long id() { return id; }
    public String guestName() { return guestName; }
    public int guestCount() { return guestCount; }
    public LocalDate checkIn() { return checkIn; }
    public LocalDate checkOut() { return checkOut; }

    @Override
    public boolean equals(Object o) {
        return o instanceof Reservation r && Objects.equals(id, r.id);
    }
    @Override
    public int hashCode() { return Objects.hash(id); }
}
