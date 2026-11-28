package com.hotel.shifthelper.application;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import com.hotel.shifthelper.domain.port.AssignmentRepository;
import com.hotel.shifthelper.domain.port.ReservationRepository;
import com.hotel.shifthelper.domain.port.RoomRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal in-memory port implementations for application-service tests. */
final class InMemoryPorts {

    private InMemoryPorts() {}

    static final class Rooms implements RoomRepository {
        final Map<Long, Room> store = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        public Optional<Room> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        public List<Room> findAllInService() { return store.values().stream().filter(Room::inService).toList(); }
        public List<Room> findAll() { return new ArrayList<>(store.values()); }
        public Room save(Room room) {
            Long id = room.id() != null ? room.id() : seq.incrementAndGet();
            Room saved = new Room(id, room.number(), room.capacity(), room.inService());
            store.put(id, saved);
            return saved;
        }
    }

    static final class Reservations implements ReservationRepository {
        final Map<Long, Reservation> store = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        public Optional<Reservation> findById(Long id) { return Optional.ofNullable(store.get(id)); }
        public Reservation save(Reservation r) {
            Long id = r.id() != null ? r.id() : seq.incrementAndGet();
            Reservation saved = new Reservation(id, r.guestName(), r.guestCount(), r.checkIn(), r.checkOut());
            store.put(id, saved);
            return saved;
        }
    }

    static final class Assignments implements AssignmentRepository {
        final Map<Long, Assignment> store = new LinkedHashMap<>();
        private final AtomicLong seq = new AtomicLong();

        public Optional<Assignment> findById(Long id) { return Optional.ofNullable(copy(store.get(id))); }
        public List<Assignment> findActiveOn(LocalDate ref) {
            return store.values().stream().filter(a -> a.checkOut().isAfter(ref)).map(Assignments::copy).toList();
        }
        public Optional<Assignment> findByReservationId(Long reservationId) {
            return store.values().stream().filter(a -> a.reservationId().equals(reservationId))
                    .findFirst().map(Assignments::copy);
        }
        public Assignment save(Assignment a) {
            Long id = a.id() != null ? a.id() : seq.incrementAndGet();
            Assignment saved = new Assignment(id, a.reservationId(), a.roomId(), a.checkIn(), a.checkOut());
            store.put(id, saved);
            return copy(saved);
        }
        Long roomOf(Long reservationId) {
            return findByReservationId(reservationId).map(Assignment::roomId).orElse(null);
        }
        private static Assignment copy(Assignment a) {
            return a == null ? null : new Assignment(a.id(), a.reservationId(), a.roomId(), a.checkIn(), a.checkOut());
        }
    }
}
