package com.hotel.shifthelper.domain.model;

import java.util.Objects;

/**
 * A physical room registered once at the start of a shift. Carries a static
 * in-service toggle (the 2-state "사용가능/사용불가") independent of any
 * date-range occupancy, which is computed from {@link Assignment}s.
 */
public final class Room {

    private final Long id;
    private final String number;
    private final int capacity;
    private boolean inService;

    public Room(Long id, String number, int capacity, boolean inService) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("Room number is required.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive.");
        }
        this.id = id;
        this.number = number;
        this.capacity = capacity;
        this.inService = inService;
    }

    public Long id() { return id; }
    public String number() { return number; }
    public int capacity() { return capacity; }
    public boolean inService() { return inService; }

    public void markOutOfService() { this.inService = false; }
    public void markInService() { this.inService = true; }

    public boolean accommodates(int guests) {
        return inService && capacity >= guests;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Room r && Objects.equals(id, r.id);
    }
    @Override
    public int hashCode() { return Objects.hash(id); }
}
