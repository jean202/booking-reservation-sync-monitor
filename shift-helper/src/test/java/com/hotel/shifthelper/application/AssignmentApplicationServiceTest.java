package com.hotel.shifthelper.application;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.AssignmentPlan;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Change;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentApplicationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private InMemoryPorts.Rooms rooms;
    private InMemoryPorts.Reservations reservations;
    private InMemoryPorts.Assignments assignments;
    private AssignmentApplicationService service;

    @BeforeEach
    void setUp() {
        rooms = new InMemoryPorts.Rooms();
        reservations = new InMemoryPorts.Reservations();
        assignments = new InMemoryPorts.Assignments();
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE);
        service = new AssignmentApplicationService(rooms, reservations, assignments, clock);
    }

    private Room room(String number, int capacity) {
        return rooms.save(new Room(null, number, capacity, true));
    }

    private Reservation reservation(String guest, int guests, int fromDay, int toDay) {
        return reservations.save(new Reservation(null, guest, guests, TODAY.plusDays(fromDay), TODAY.plusDays(toDay)));
    }

    private void place(Reservation r, Room room) {
        assignments.save(new Assignment(null, r.id(), room.id(), r.checkIn(), r.checkOut()));
    }

    @Test
    void previewProposesRoomWithoutPersisting() {
        Room r101 = room("101", 2);
        Reservation lee = reservation("Lee", 2, 3, 5);

        AssignmentPlan plan = service.preview(lee.id());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.placements()).singleElement().satisfies(p -> {
            assertThat(p.roomId()).isEqualTo(r101.id());
            assertThat(p.change()).isEqualTo(Change.NEW);
        });
        assertThat(assignments.store).isEmpty();
    }

    @Test
    void commitCreatesAssignmentForTarget() {
        Room r101 = room("101", 2);
        Reservation lee = reservation("Lee", 2, 3, 5);

        service.commit(lee.id());

        Assignment saved = assignments.findByReservationId(lee.id()).orElseThrow();
        assertThat(saved.roomId()).isEqualTo(r101.id());
        assertThat(saved.checkIn()).isEqualTo(lee.checkIn());
        assertThat(saved.checkOut()).isEqualTo(lee.checkOut());
    }

    @Test
    void commitMovesPreArrivalGuestToMakeRoomForLargerParty() {
        Room r101 = room("101", 2);
        Room r102 = room("102", 4);
        Room r103 = room("103", 3);
        Reservation kim = reservation("Kim", 3, 4, 6);
        place(kim, r102);
        Reservation lee = reservation("Lee", 4, 4, 6);

        AssignmentPlan plan = service.commit(lee.id());

        assertThat(plan.movedPlacements()).extracting(p -> p.reservationId()).containsExactly(kim.id());
        assertThat(assignments.roomOf(kim.id())).isEqualTo(r103.id());
        assertThat(assignments.roomOf(lee.id())).isEqualTo(r102.id());
        assertThat(assignments.store).hasSize(2);
        assertThat(r101.id()).isNotIn(assignments.roomOf(kim.id()), assignments.roomOf(lee.id()));
    }

    @Test
    void checkedInGuestIsNotMovedByCommit() {
        Room r101 = room("101", 4);
        room("102", 4);
        Reservation inHouse = reservation("Kim", 2, -1, 3);
        place(inHouse, r101);
        Reservation lee = reservation("Lee", 2, 1, 2);

        AssignmentPlan plan = service.commit(lee.id());

        assertThat(assignments.roomOf(inHouse.id())).isEqualTo(r101.id());
        assertThat(plan.placements()).filteredOn(p -> p.reservationId().equals(inHouse.id()))
                .singleElement().extracting(p -> p.change()).isEqualTo(Change.LOCKED);
    }

    @Test
    void outOfServiceRoomsAreNotUsed() {
        Room r101 = room("101", 2);
        Room r102 = room("102", 2);
        rooms.save(new Room(r101.id(), r101.number(), r101.capacity(), false));
        Reservation lee = reservation("Lee", 2, 1, 2);

        AssignmentPlan plan = service.preview(lee.id());

        assertThat(plan.placements()).singleElement().extracting(p -> p.roomId()).isEqualTo(r102.id());
    }

    @Test
    void noInServiceRoomsIsInfeasibleAndCommitConflicts() {
        Reservation lee = reservation("Lee", 2, 1, 2);

        assertThat(service.preview(lee.id()).feasible()).isFalse();
        assertStatus(() -> service.commit(lee.id()), HttpStatus.CONFLICT);
        assertThat(assignments.store).isEmpty();
    }

    @Test
    void checkedOutAssignmentsAreIgnored() {
        Room r101 = room("101", 2);
        Reservation past = reservation("Old", 2, -5, -2);
        place(past, r101);
        Reservation lee = reservation("Lee", 2, 1, 2);

        AssignmentPlan plan = service.preview(lee.id());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.placements()).extracting(p -> p.reservationId()).containsExactly(lee.id());
    }

    @Test
    void assignmentWhoseReservationVanishedIsSkipped() {
        Room r101 = room("101", 2);
        assignments.save(new Assignment(null, 999L, r101.id(), TODAY.plusDays(1), TODAY.plusDays(3)));
        Reservation lee = reservation("Lee", 2, 1, 3);

        AssignmentPlan plan = service.preview(lee.id());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.placements()).extracting(p -> p.reservationId()).containsExactly(lee.id());
    }

    @Test
    void unknownReservationIsNotFound() {
        assertStatus(() -> service.preview(42L), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.commit(42L), HttpStatus.NOT_FOUND);
    }

    @Test
    void alreadyAssignedReservationConflicts() {
        Room r101 = room("101", 2);
        Reservation lee = reservation("Lee", 2, 1, 2);
        service.commit(lee.id());

        assertStatus(() -> service.preview(lee.id()), HttpStatus.CONFLICT);
        assertStatus(() -> service.commit(lee.id()), HttpStatus.CONFLICT);
        assertThat(assignments.store).hasSize(1);
        assertThat(assignments.roomOf(lee.id())).isEqualTo(r101.id());
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }
}
