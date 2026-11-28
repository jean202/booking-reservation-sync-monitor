package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Room;
import jakarta.persistence.*;

@Entity
@Table(name = "shift_room")
class RoomJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String number;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean inService;

    protected RoomJpaEntity() {}

    static RoomJpaEntity fromDomain(Room r) {
        RoomJpaEntity e = new RoomJpaEntity();
        e.id = r.id();
        e.number = r.number();
        e.capacity = r.capacity();
        e.inService = r.inService();
        return e;
    }

    Room toDomain() {
        return new Room(id, number, capacity, inService);
    }

    Long id() { return id; }
}
