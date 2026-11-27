package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import com.hotel.syncmonitor.dto.AssignmentPlanDto;
import com.hotel.syncmonitor.dto.AssignmentPlanDto.Placement;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hotel.syncmonitor.Fixtures.doubleReservation;
import static com.hotel.syncmonitor.Fixtures.doubleRoom;
import static com.hotel.syncmonitor.Fixtures.reservation;
import static com.hotel.syncmonitor.Fixtures.room;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AutoAssignmentService reads LocalDate.now() directly, so every stay here is
 * expressed relative to today.
 */
@DataJpaTest(showSql = false)
class AutoAssignmentServiceTest {

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private RoomAssignmentRepository roomAssignmentRepository;
    @Autowired private ReservationSyncLogRepository syncLogRepository;

    private AutoAssignmentService service;

    private static LocalDate d(int days) {
        return LocalDate.now().plusDays(days);
    }

    @BeforeEach
    void setUp() {
        service = new AutoAssignmentService(reservationRepository, roomRepository,
                roomAssignmentRepository, new SyncLogService(syncLogRepository));
    }

    @Test
    void assignsNewReservationWithoutMovingAnyoneWhenARoomIsFree() {
        roomRepository.save(doubleRoom("201"));
        Reservation target = reservationRepository.save(doubleReservation("T", d(2), d(4)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.targetReservationId()).isEqualTo(target.getId());
        assertThat(plan.roomType()).isEqualTo(RoomType.DOUBLE);
        assertThat(plan.movedPlacements()).isEmpty();
        assertThat(plan.placements()).singleElement().satisfies(p -> {
            assertThat(p.change()).isEqualTo("NEW");
            assertThat(p.roomNumber()).isEqualTo("201");
        });
        assertThat(plan.message()).isEqualTo("Assignable without moving anyone.");
    }

    @Test
    void reshufflesPreArrivalAssignmentsWhenNoRoomIsFreeForWholeStay() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        Room r202 = roomRepository.save(doubleRoom("202"));
        Room r203 = roomRepository.save(doubleRoom("203"));
        assign(reservationRepository.save(doubleReservation("A", d(1), d(3))), r201);
        assign(reservationRepository.save(doubleReservation("B", d(3), d(5))), r202);
        assign(reservationRepository.save(doubleReservation("C", d(5), d(7))), r203);
        Reservation target = reservationRepository.save(doubleReservation("T", d(2), d(6)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.movedPlacements()).isNotEmpty()
                .allSatisfy(p -> {
                    assertThat(p.change()).isEqualTo("MOVED");
                    assertThat(p.previousRoomNumber()).isNotNull().isNotEqualTo(p.roomNumber());
                });
        assertThat(placementOf(plan, "T").change()).isEqualTo("NEW");
        assertThat(plan.placements()).hasSize(4);
        assertNoOverlapPerRoom(plan.placements());
        assertThat(plan.message()).contains(plan.movedPlacements().size() + " pre-arrival assignment(s) will move");
    }

    @Test
    void previewDoesNotPersistAnything() {
        roomRepository.save(doubleRoom("201"));
        Reservation target = reservationRepository.save(doubleReservation("T", d(2), d(4)));

        service.preview(target.getId());

        assertThat(roomAssignmentRepository.count()).isZero();
        assertThat(syncLogRepository.count()).isZero();
    }

    @Test
    void commitPersistsNewAndMovedAssignmentsAndLogs() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        Room r202 = roomRepository.save(doubleRoom("202"));
        Room r203 = roomRepository.save(doubleRoom("203"));
        assign(reservationRepository.save(doubleReservation("A", d(1), d(3))), r201);
        assign(reservationRepository.save(doubleReservation("B", d(3), d(5))), r202);
        assign(reservationRepository.save(doubleReservation("C", d(5), d(7))), r203);
        Reservation target = reservationRepository.save(doubleReservation("T", d(2), d(6)));

        AssignmentPlanDto plan = service.commit(target.getId());

        List<RoomAssignment> stored = roomAssignmentRepository.findActiveByRoomType(RoomType.DOUBLE, LocalDate.now());
        assertThat(stored).hasSize(4);
        Map<String, String> roomByGuest = stored.stream().collect(Collectors.toMap(
                a -> a.getReservation().getExternalReservationId(), a -> a.getRoom().getRoomNumber()));
        for (Placement p : plan.placements()) {
            assertThat(roomByGuest).containsEntry(p.externalReservationId(), p.roomNumber());
        }
        assertThat(roomAssignmentRepository.findByReservationIdAndAssignmentStatus(target.getId(), AssignmentStatus.ASSIGNED))
                .singleElement().satisfies(a -> {
                    assertThat(a.getAssignedCheckIn()).isEqualTo(d(2));
                    assertThat(a.getAssignedCheckOut()).isEqualTo(d(6));
                });
        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("T"))
                .extracting(ReservationSyncLog::getEventType).containsExactly("AUTO_ASSIGNED");
    }

    @Test
    void checkedInGuestIsLockedToCurrentRoom() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        roomRepository.save(doubleRoom("202"));
        assign(reservationRepository.save(doubleReservation("IN_HOUSE", d(-1), d(2))), r201);
        Reservation target = reservationRepository.save(doubleReservation("T", d(0), d(2)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isTrue();
        Placement locked = placementOf(plan, "IN_HOUSE");
        assertThat(locked.change()).isEqualTo("LOCKED");
        assertThat(locked.roomNumber()).isEqualTo("201");
        assertThat(placementOf(plan, "T").roomNumber()).isEqualTo("202");
    }

    @Test
    void infeasibleWhenCheckedInGuestOccupiesTheOnlyRoom() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        assign(reservationRepository.save(doubleReservation("IN_HOUSE", d(-1), d(3))), r201);
        Reservation target = reservationRepository.save(doubleReservation("T", d(1), d(2)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.placements()).isEmpty();
        assertThat(plan.message()).contains("No feasible placement");
    }

    @Test
    void commitOfInfeasiblePlanIsConflictAndChangesNothing() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        assign(reservationRepository.save(doubleReservation("IN_HOUSE", d(-1), d(3))), r201);
        Reservation target = reservationRepository.save(doubleReservation("T", d(1), d(2)));

        assertStatus(() -> service.commit(target.getId()), HttpStatus.CONFLICT);
        assertThat(roomAssignmentRepository.count()).isEqualTo(1);
        assertThat(syncLogRepository.count()).isZero();
    }

    @Test
    void checkedOutAssignmentsDoNotBlockRooms() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        assign(reservationRepository.save(doubleReservation("PAST", d(-5), d(-2))), r201);
        Reservation target = reservationRepository.save(doubleReservation("T", d(1), d(3)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.placements()).extracting(Placement::externalReservationId).containsExactly("T");
    }

    @Test
    void otherRoomTypesAreNeitherCandidatesNorBlockers() {
        roomRepository.save(room("301", RoomType.TWIN, 2));
        Room r201 = roomRepository.save(doubleRoom("201"));
        Reservation twinGuest = reservationRepository.save(reservation("TW", RoomType.TWIN, 2, d(1), d(3)));
        assign(twinGuest, roomRepository.findAll().stream()
                .filter(r -> r.getRoomType() == RoomType.TWIN).findFirst().orElseThrow());
        Reservation target = reservationRepository.save(doubleReservation("T", d(1), d(3)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.placements()).singleElement().satisfies(p -> {
            assertThat(p.roomId()).isEqualTo(r201.getId());
            assertThat(p.externalReservationId()).isEqualTo("T");
        });
    }

    @Test
    void infeasibleWhenNoRoomOfTypeHasEnoughCapacity() {
        roomRepository.save(doubleRoom("201"));
        Reservation target = reservationRepository.save(reservation("T", RoomType.DOUBLE, 3, d(1), d(2)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.message()).contains("No active rooms of type DOUBLE").contains("3");
    }

    @Test
    void acknowledgedReservationIsAccepted() {
        roomRepository.save(doubleRoom("201"));
        Reservation ack = doubleReservation("T", d(1), d(2));
        ack.setPmsSyncStatus(PmsSyncStatus.ACKNOWLEDGED);
        Reservation target = reservationRepository.save(ack);

        assertThat(service.preview(target.getId()).feasible()).isTrue();
    }

    @Test
    void pendingImportIsRejected() {
        roomRepository.save(doubleRoom("201"));
        Reservation pending = doubleReservation("T", d(1), d(2));
        pending.setPmsSyncStatus(PmsSyncStatus.PENDING_IMPORT);
        Reservation target = reservationRepository.save(pending);

        assertStatus(() -> service.preview(target.getId()), HttpStatus.CONFLICT);
        assertStatus(() -> service.commit(target.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void alreadyAssignedReservationIsRejected() {
        Room r201 = roomRepository.save(doubleRoom("201"));
        Reservation target = reservationRepository.save(doubleReservation("T", d(1), d(2)));
        assign(target, r201);

        assertStatus(() -> service.preview(target.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void unknownReservationIsNotFound() {
        assertStatus(() -> service.preview(999L), HttpStatus.NOT_FOUND);
    }

    @Disabled("FINDINGS #3: 재배치 대상 예약의 인원수를 무시하고 정원이 작은 객실로 옮긴다")
    @Test
    void reshuffleNeverPlacesAPartyInARoomBelowItsSize() {
        Room small = roomRepository.save(room("201", RoomType.DOUBLE, 2));
        Room large = roomRepository.save(room("202", RoomType.DOUBLE, 4));
        Reservation bigParty = reservationRepository.save(reservation("BIG", RoomType.DOUBLE, 4, d(3), d(5)));
        assign(bigParty, large);
        Reservation target = reservationRepository.save(reservation("T", RoomType.DOUBLE, 2, d(6), d(8)));

        AssignmentPlanDto plan = service.preview(target.getId());

        Map<Long, Integer> capacity = Map.of(small.getId(), 2, large.getId(), 4);
        Map<String, Integer> guests = Map.of("BIG", 4, "T", 2);
        assertThat(plan.placements()).allSatisfy(p ->
                assertThat(capacity.get(p.roomId())).isGreaterThanOrEqualTo(guests.get(p.externalReservationId())));
    }

    @Disabled("FINDINGS #7: 현재 방을 유지해도 되는 사전도착 배정을 불필요하게 MOVED로 옮긴다")
    @Test
    void preArrivalAssignmentKeepsItsRoomWhenNoMoveIsNeeded() {
        roomRepository.save(doubleRoom("201"));
        Room r202 = roomRepository.save(doubleRoom("202"));
        assign(reservationRepository.save(doubleReservation("KIM", d(1), d(3))), r202);
        Reservation target = reservationRepository.save(doubleReservation("T", d(3), d(5)));

        AssignmentPlanDto plan = service.preview(target.getId());

        assertThat(plan.feasible()).isTrue();
        assertThat(placementOf(plan, "KIM").change()).isEqualTo("UNCHANGED");
        assertThat(plan.movedPlacements()).isEmpty();
    }

    // ---------- helpers ----------

    private RoomAssignment assign(Reservation r, Room room) {
        RoomAssignment a = new RoomAssignment();
        a.setReservation(r);
        a.setRoom(room);
        a.setAssignedCheckIn(r.getCheckInDate());
        a.setAssignedCheckOut(r.getCheckOutDate());
        a.setAssignmentStatus(AssignmentStatus.ASSIGNED);
        return roomAssignmentRepository.save(a);
    }

    private static Placement placementOf(AssignmentPlanDto plan, String externalId) {
        return plan.placements().stream()
                .filter(p -> p.externalReservationId().equals(externalId))
                .findFirst().orElseThrow(() -> new AssertionError("no placement for " + externalId));
    }

    private static void assertNoOverlapPerRoom(List<Placement> placements) {
        Map<Long, List<Placement>> byRoom = new HashMap<>();
        placements.forEach(p -> byRoom.computeIfAbsent(p.roomId(), k -> new java.util.ArrayList<>()).add(p));
        byRoom.values().forEach(list -> {
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    Placement a = list.get(i), b = list.get(j);
                    assertThat(a.checkInDate().isBefore(b.checkOutDate()) && a.checkOutDate().isAfter(b.checkInDate()))
                            .as("%s overlaps %s in room %s", a.externalReservationId(), b.externalReservationId(), a.roomNumber())
                            .isFalse();
                }
            }
        });
    }

    private static void assertStatus(ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }
}
