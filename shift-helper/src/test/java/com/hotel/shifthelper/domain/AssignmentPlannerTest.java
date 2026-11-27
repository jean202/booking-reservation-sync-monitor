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

    @Test
    void back_to_back_stay_reuses_the_room_with_smallest_gap() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 2, true);
        // Checked-in guest leaving on CHECK_IN from room 102.
        ActivePlacement leaving = new ActivePlacement(
                1L, "Kim", 2, r102.id(), TODAY, CHECK_IN);

        Reservation next = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(next, List.of(r101, r102), List.of(leaving), TODAY);

        assertThat(plan.feasible()).isTrue();
        assertThat(placementFor(plan, 2L).roomNumber()).isEqualTo("102");
        assertThat(plan.movedPlacements()).isEmpty();
        assertThat(plan.message()).isEqualTo("Assignable without moving any pre-arrival guest.");
    }

    @org.junit.jupiter.api.Disabled("FINDINGS #7: 현재 방을 유지해도 되는 사전도착 배정을 불필요하게 MOVED로 옮긴다")
    @Test
    void pre_arrival_guest_keeps_its_room_when_no_move_is_needed() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 2, true);
        ActivePlacement kim = new ActivePlacement(
                1L, "Kim", 2, r102.id(), CHECK_IN.minusDays(2), CHECK_IN);

        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(lee, List.of(r101, r102), List.of(kim), TODAY);

        assertThat(plan.feasible()).isTrue();
        assertThat(placementFor(plan, 1L).change()).isEqualTo(Change.UNCHANGED);
        assertThat(plan.movedPlacements()).isEmpty();
    }

    @Test
    void locked_placements_are_reported_with_their_room() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 2, true);
        ActivePlacement inHouse = new ActivePlacement(
                1L, "Kim", 2, r101.id(), TODAY.minusDays(1), CHECK_OUT);

        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(lee, List.of(r101, r102), List.of(inHouse), TODAY);

        Placement locked = placementFor(plan, 1L);
        assertThat(locked.change()).isEqualTo(Change.LOCKED);
        assertThat(locked.roomNumber()).isEqualTo("101");
        assertThat(placementFor(plan, 2L).roomNumber()).isEqualTo("102");
    }

    @Test
    void guest_checking_in_today_is_locked() {
        Room r101 = new Room(1L, "101", 2, true);
        ActivePlacement arrivingToday = new ActivePlacement(
                1L, "Kim", 2, r101.id(), TODAY, TODAY.plusDays(2));
        Reservation lee = new Reservation(2L, "Lee", 2, TODAY.plusDays(2), TODAY.plusDays(3));

        AssignmentPlan plan = planner.plan(lee, List.of(r101), List.of(arrivingToday), TODAY);

        assertThat(placementFor(plan, 1L).change()).isEqualTo(Change.LOCKED);
        assertThat(placementFor(plan, 2L).roomNumber()).isEqualTo("101");
    }

    @Test
    void locked_guest_in_room_no_longer_listed_keeps_unknown_room_number() {
        Room r101 = new Room(1L, "101", 2, true);
        ActivePlacement inRemovedRoom = new ActivePlacement(
                1L, "Kim", 2, 99L, TODAY.minusDays(1), CHECK_OUT);
        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(lee, List.of(r101), List.of(inRemovedRoom), TODAY);

        assertThat(plan.feasible()).isTrue();
        assertThat(placementFor(plan, 1L).roomNumber()).isEqualTo("?");
    }

    @Test
    void every_feasible_plan_respects_capacity_and_has_no_overlap() {
        Room r101 = new Room(1L, "101", 2, true);
        Room r102 = new Room(2L, "102", 4, true);
        Room r103 = new Room(3L, "103", 3, true);
        List<ActivePlacement> active = List.of(
                new ActivePlacement(1L, "A", 2, r102.id(), CHECK_IN, CHECK_OUT),
                new ActivePlacement(3L, "C", 3, r103.id(), CHECK_IN.minusDays(1), CHECK_IN.plusDays(1)),
                new ActivePlacement(4L, "D", 1, r101.id(), CHECK_OUT, CHECK_OUT.plusDays(2)));
        Reservation target = new Reservation(2L, "B", 4, CHECK_IN.plusDays(1), CHECK_OUT.plusDays(1));

        AssignmentPlan plan = planner.plan(target, List.of(r101, r102, r103), active, TODAY);

        assertThat(plan.feasible()).isTrue();
        java.util.Map<Long, Integer> cap = java.util.Map.of(1L, 2, 2L, 4, 3L, 3);
        java.util.Map<Long, Integer> guests = java.util.Map.of(1L, 2, 2L, 4, 3L, 3, 4L, 1);
        assertThat(plan.placements()).allSatisfy(p ->
                assertThat(cap.get(p.roomId())).isGreaterThanOrEqualTo(guests.get(p.reservationId())));
        for (Placement a : plan.placements()) {
            for (Placement b : plan.placements()) {
                if (a == b || !a.roomId().equals(b.roomId())) continue;
                assertThat(a.checkIn().isBefore(b.checkOut()) && a.checkOut().isAfter(b.checkIn()))
                        .as("%s vs %s", a.guestName(), b.guestName()).isFalse();
            }
        }
        assertThat(plan.movedPlacements()).allSatisfy(p -> assertThat(p.change()).isEqualTo(Change.MOVED));
    }

    @Test
    void infeasible_message_names_the_guest_that_could_not_be_placed() {
        Room r101 = new Room(1L, "101", 2, true);
        ActivePlacement inHouse = new ActivePlacement(1L, "Kim", 2, r101.id(), TODAY.minusDays(1), CHECK_OUT);
        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_IN, CHECK_OUT);

        AssignmentPlan plan = planner.plan(lee, List.of(r101), List.of(inHouse), TODAY);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.message()).contains("Lee");
    }

    @org.junit.jupiter.api.Disabled("FINDINGS #6: 사용불가로 바뀐 객실에서 옮겨지는 예약은 MOVED인데 previousRoomNumber가 null")
    @Test
    void moved_out_of_an_out_of_service_room_reports_previous_room_number() {
        Room r101 = new Room(1L, "101", 2, true);
        // Kim's pre-arrival placement is in room 99, which was taken out of service
        // and therefore is not in the in-service list passed to the planner.
        ActivePlacement kim = new ActivePlacement(1L, "Kim", 2, 99L, CHECK_IN, CHECK_OUT);
        Reservation lee = new Reservation(2L, "Lee", 2, CHECK_OUT, CHECK_OUT.plusDays(1));

        AssignmentPlan plan = planner.plan(lee, List.of(r101), List.of(kim), TODAY);

        Placement moved = placementFor(plan, 1L);
        assertThat(moved.change()).isEqualTo(Change.MOVED);
        assertThat(moved.previousRoomNumber()).isNotNull();
    }
}
