package com.hotel.shifthelper.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    private static final LocalDate IN = LocalDate.of(2026, 6, 6);
    private static final LocalDate OUT = LocalDate.of(2026, 6, 8);

    // ---------- Reservation ----------

    @Test
    void reservationRejectsMissingOrBlankGuestName() {
        assertThatThrownBy(() -> new Reservation(null, null, 1, IN, OUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Guest name is required.");
        assertThatThrownBy(() -> new Reservation(null, "  ", 1, IN, OUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Guest name is required.");
    }

    @Test
    void reservationRejectsNonPositiveGuestCount() {
        assertThatThrownBy(() -> new Reservation(null, "Kim", 0, IN, OUT))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Guest count must be positive.");
    }

    @Test
    void reservationRequiresBothDates() {
        assertThatThrownBy(() -> new Reservation(null, "Kim", 1, null, OUT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Reservation(null, "Kim", 1, IN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reservationRequiresCheckOutAfterCheckIn() {
        assertThatThrownBy(() -> new Reservation(null, "Kim", 1, IN, IN))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Check-out must be after check-in.");
        assertThatThrownBy(() -> new Reservation(null, "Kim", 1, OUT, IN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneNightReservationIsValid() {
        Reservation r = new Reservation(1L, "Kim", 2, IN, IN.plusDays(1));

        assertThat(r.guestName()).isEqualTo("Kim");
        assertThat(r.guestCount()).isEqualTo(2);
        assertThat(r.checkOut()).isEqualTo(IN.plusDays(1));
    }

    @Test
    void reservationsAreEqualById() {
        assertThat(new Reservation(1L, "Kim", 1, IN, OUT)).isEqualTo(new Reservation(1L, "Lee", 3, IN, OUT.plusDays(1)))
                .hasSameHashCodeAs(new Reservation(1L, "Lee", 3, IN, OUT.plusDays(1)));
        assertThat(new Reservation(1L, "Kim", 1, IN, OUT)).isNotEqualTo(new Reservation(2L, "Kim", 1, IN, OUT));
    }

    // ---------- Room ----------

    @Test
    void roomRejectsBlankNumberAndNonPositiveCapacity() {
        assertThatThrownBy(() -> new Room(null, "", 2, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Room number is required.");
        assertThatThrownBy(() -> new Room(null, null, 2, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Room(null, "101", 0, true))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Capacity must be positive.");
    }

    @Test
    void roomAccommodatesOnlyWhenInServiceAndLargeEnough() {
        Room room = new Room(1L, "101", 3, true);

        assertThat(room.accommodates(3)).isTrue();
        assertThat(room.accommodates(4)).isFalse();
        room.markOutOfService();
        assertThat(room.inService()).isFalse();
        assertThat(room.accommodates(1)).isFalse();
        room.markInService();
        assertThat(room.accommodates(1)).isTrue();
    }

    // ---------- Assignment ----------

    @Test
    void assignmentRequiresReservationRoomAndValidInterval() {
        assertThatThrownBy(() -> new Assignment(null, null, 1L, IN, OUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Assignment(null, 1L, null, IN, OUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Assignment(null, 1L, 1L, null, OUT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Assignment(null, 1L, 1L, IN, IN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assignmentCanMoveToAnotherRoomButNotToNull() {
        Assignment a = new Assignment(1L, 10L, 100L, IN, OUT);

        a.moveTo(200L);

        assertThat(a.roomId()).isEqualTo(200L);
        assertThatThrownBy(() -> a.moveTo(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(a.roomId()).isEqualTo(200L);
    }

    @Test
    void backToBackStaysDoNotOverlap() {
        Assignment a = new Assignment(1L, 10L, 100L, IN, OUT);

        assertThat(a.overlaps(OUT, OUT.plusDays(2))).isFalse();
        assertThat(a.overlaps(IN.minusDays(2), IN)).isFalse();
        assertThat(a.overlaps(IN.plusDays(1), OUT.plusDays(1))).isTrue();
        assertThat(a.overlaps(IN.minusDays(1), OUT.plusDays(1))).isTrue();
    }

    @Test
    void infeasiblePlanHasNoPlacements() {
        AssignmentPlan plan = AssignmentPlan.infeasible(5L, "nope");

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.targetReservationId()).isEqualTo(5L);
        assertThat(plan.placements()).isEmpty();
        assertThat(plan.movedPlacements()).isEmpty();
        assertThat(plan.message()).isEqualTo("nope");
    }
}
