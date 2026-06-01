package com.hotel.shifthelper.domain;

import com.hotel.shifthelper.domain.AssignmentPlanner.ActivePlacement;
import com.hotel.shifthelper.domain.model.AssignmentPlan;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Change;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Placement;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentPlannerTest {

    private final AssignmentPlanner planner = new AssignmentPlanner();
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 6, 6);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 6, 8);

    private Placement placementFor(AssignmentPlan plan, Long reservationId) {
        return plan.placements().stream()
                .filter(p -> p.reservationId().equals(reservationId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no placement for reservation " + reservationId));
    }

    @Test
    void reshuffles_small_party_out_of_large_room_to_fit_a_bigger_party() {
        // Rooms: 101(cap2), 102(cap4), 103(cap3).
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 4, true);
        Room r103 = new Room(3L, "103", 3, true);

        // Kim (3 guests) is pre-arrival and currently parked in the only cap-4 room.
        ActivePlacement kim = new ActivePlacement(
                1L, "Kim", 3, r102.id(), CHECK_IN, CHECK_OUT);

        // Lee (4 guests) needs the cap-4 room; the only feasible plan moves
        // Kim into 103 (cap3) and puts Lee in 102 (cap4).
        Reservation lee = new Reservation(2L, "Lee", 4, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(
                lee, List.of(r101, r102, r103), List.of(kim), TODAY);

        assertThat(plan.feasible()).isTrue();
        assertThat(placementFor(plan, 2L).roomNumber()).isEqualTo("102");
        Placement kimPlacement = placementFor(plan, 1L);
        assertThat(kimPlacement.roomNumber()).isEqualTo("103");
        assertThat(kimPlacement.change()).isEqualTo(Change.MOVED);
        assertThat(plan.movedPlacements()).extracting(Placement::reservationId)
                .containsExactly(1L);
    }

    @Test
    void best_fit_assigns_the_smallest_room_that_accommodates() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 4, true);
        Room r103 = new Room(3L, "103", 3, true);

        Reservation party2 = new Reservation(2L, "Park", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(
                party2, List.of(r101, r102, r103), List.of(), TODAY);

        assertThat(plan.feasible()).isTrue();
        Placement p = placementFor(plan, 2L);
        assertThat(p.roomNumber()).isEqualTo("101");
        assertThat(p.change()).isEqualTo(Change.NEW);
        assertThat(plan.movedPlacements()).isEmpty();
    }

    @Test
    void infeasible_when_party_exceeds_every_room_capacity() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 4, true);

        Reservation party5 = new Reservation(2L, "Choi", 5, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(
                party5, List.of(r101, r102), List.of(), TODAY);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.placements()).isEmpty();
    }

    @Test
    void checked_in_guest_is_locked_and_blocks_its_room() {
        Room r102 = new Room(2L, "102", 4, true);

        // Already checked in (checkIn not after today) -> locked to 102.
        ActivePlacement arrived = new ActivePlacement(
                1L, "Kim", 3, r102.id(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7));

        // New 4-guest party overlapping the locked stay; only room is 102 -> infeasible.
        Reservation lee = new Reservation(2L, "Lee", 4, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(
                lee, List.of(r102), List.of(arrived), TODAY);

        assertThat(plan.feasible()).isFalse();
    }

    @Test
    void no_rooms_registered_is_infeasible() {
        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(lee, List.of(), List.of(), TODAY);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.message()).contains("No in-service rooms");
    }
}
