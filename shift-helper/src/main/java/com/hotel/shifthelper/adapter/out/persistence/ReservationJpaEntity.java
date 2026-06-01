package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Reservation;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "shift_reservation")
class ReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String guestName;

    @Column(nullable = false)
    private int guestCount;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    protected ReservationJpaEntity() {}

    static ReservationJpaEntity fromDomain(Reservation r) {
        ReservationJpaEntity e = new ReservationJpaEntity();
        e.id = r.id();
        e.guestName = r.guestName();
        e.guestCount = r.guestCount();
        e.checkIn = r.checkIn();
        e.checkOut = r.checkOut();
        return e;
    }

    Reservation toDomain() {
        return new Reservation(id, guestName, guestCount, checkIn, checkOut);
    }
}
