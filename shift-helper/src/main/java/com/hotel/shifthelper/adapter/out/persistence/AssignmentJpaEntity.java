package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Assignment;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "shift_assignment")
class AssignmentJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    protected AssignmentJpaEntity() {}

    static AssignmentJpaEntity fromDomain(Assignment a) {
        AssignmentJpaEntity e = new AssignmentJpaEntity();
        e.id = a.id();
        e.reservationId = a.reservationId();
        e.roomId = a.roomId();
        e.checkIn = a.checkIn();
        e.checkOut = a.checkOut();
        return e;
    }

    Assignment toDomain() {
        return new Assignment(id, reservationId, roomId, checkIn, checkOut);
    }
}
