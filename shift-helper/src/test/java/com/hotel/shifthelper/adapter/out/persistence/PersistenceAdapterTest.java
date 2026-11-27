package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
@Import({RoomPersistenceAdapter.class, ReservationPersistenceAdapter.class, AssignmentPersistenceAdapter.class})
class PersistenceAdapterTest {

    private static final LocalDate REF = LocalDate.of(2026, 6, 2);

    @Autowired private RoomPersistenceAdapter rooms;
    @Autowired private ReservationPersistenceAdapter reservations;
    @Autowired private AssignmentPersistenceAdapter assignments;
    @Autowired private SpringDataRoomRepository roomJpa;

    @Test
    void roomRoundTripsAndInServiceFilterApplies() {
        Room a = rooms.save(new Room(null, "101", 2, true));
        Room b = rooms.save(new Room(null, "102", 4, false));

        assertThat(a.id()).isNotNull();
        assertThat(rooms.findById(b.id())).get().satisfies(r -> {
            assertThat(r.number()).isEqualTo("102");
            assertThat(r.capacity()).isEqualTo(4);
            assertThat(r.inService()).isFalse();
        });
        assertThat(rooms.findAll()).hasSize(2);
        assertThat(rooms.findAllInService()).extracting(Room::number).containsExactly("101");
    }

    @Test
    void savingExistingRoomUpdatesInsteadOfInserting() {
        Room a = rooms.save(new Room(null, "101", 2, true));
        a.markOutOfService();

        rooms.save(a);

        assertThat(rooms.findAll()).singleElement().satisfies(r -> assertThat(r.inService()).isFalse());
    }

    @Test
    void duplicateRoomNumberIsRejectedByStore() {
        rooms.save(new Room(null, "101", 2, true));

        assertThatThrownBy(() -> {
            rooms.save(new Room(null, "101", 3, true));
            roomJpa.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void reservationRoundTrips() {
        Reservation saved = reservations.save(new Reservation(null, "Kim", 3, REF, REF.plusDays(2)));

        assertThat(reservations.findById(saved.id())).get().satisfies(r -> {
            assertThat(r.guestName()).isEqualTo("Kim");
            assertThat(r.guestCount()).isEqualTo(3);
            assertThat(r.checkIn()).isEqualTo(REF);
            assertThat(r.checkOut()).isEqualTo(REF.plusDays(2));
        });
        assertThat(reservations.findById(999L)).isEmpty();
    }

    @Test
    void activeAssignmentsExcludeThoseCheckingOutOnOrBeforeReference() {
        assignments.save(new Assignment(null, 1L, 1L, REF.minusDays(2), REF));
        assignments.save(new Assignment(null, 2L, 1L, REF.minusDays(1), REF.plusDays(1)));
        assignments.save(new Assignment(null, 3L, 1L, REF.plusDays(3), REF.plusDays(4)));

        assertThat(assignments.findActiveOn(REF)).extracting(Assignment::reservationId)
                .containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void assignmentMoveIsPersistedOnSameRow() {
        Assignment saved = assignments.save(new Assignment(null, 1L, 10L, REF, REF.plusDays(1)));
        saved.moveTo(20L);

        assignments.save(saved);

        assertThat(assignments.findByReservationId(1L)).get().satisfies(a -> {
            assertThat(a.id()).isEqualTo(saved.id());
            assertThat(a.roomId()).isEqualTo(20L);
        });
        assertThat(assignments.findById(saved.id())).isPresent();
        assertThat(assignments.findByReservationId(2L)).isEmpty();
    }
}
