package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.RoomCandidateDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false)
class RoomAssignmentServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    private RoomAssignmentService roomAssignmentService;

    @BeforeEach
    void setUp() {
        roomAssignmentService = new RoomAssignmentService(
                reservationRepository,
                roomRepository,
                roomAssignmentRepository,
                new SyncLogService(syncLogRepository)
        );
    }

    @Test
    void overlappingAssignedRoomIsNotRecommendedAndCannotBeAssignedAgain() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Room room202 = roomRepository.save(room("202", RoomType.DOUBLE));
        Reservation first = reservationRepository.save(reservation(
                "BKG-001", LocalDate.of(2026, 5, 23), LocalDate.of(2026, 5, 25)
        ));
        Reservation overlapping = reservationRepository.save(reservation(
                "BKG-002", LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 26)
        ));

        roomAssignmentService.assignRoom(first.getId(), room201.getId());

        List<RoomCandidateDto> candidates = roomAssignmentService.getAvailableRooms(overlapping.getId());

        assertThat(candidates)
                .extracting(RoomCandidateDto::roomNumber)
                .containsExactly("202");

        assertThatThrownBy(() -> roomAssignmentService.assignRoom(overlapping.getId(), room201.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private static final LocalDate IN = LocalDate.of(2026, 5, 23);
    private static final LocalDate OUT = LocalDate.of(2026, 5, 25);

    @Test
    void backToBackStayCanReuseSameRoom() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation first = reservationRepository.save(reservation("BKG-001", IN, OUT));
        Reservation next = reservationRepository.save(reservation("BKG-002", OUT, OUT.plusDays(2)));
        roomAssignmentService.assignRoom(first.getId(), room201.getId());

        assertThat(roomAssignmentService.getAvailableRooms(next.getId()))
                .extracting(RoomCandidateDto::roomNumber).containsExactly("201");
        RoomAssignment assignment = roomAssignmentService.assignRoom(next.getId(), room201.getId());
        assertThat(assignment.getAssignedCheckIn()).isEqualTo(OUT);
    }

    @Test
    void candidatesExcludeOtherTypesInactiveRoomsAndTooSmallRooms() {
        roomRepository.save(room("201", RoomType.DOUBLE));
        roomRepository.save(room("301", RoomType.TWIN));
        Room inactive = room("202", RoomType.DOUBLE);
        inactive.setStatus(RoomStatus.INACTIVE);
        roomRepository.save(inactive);
        Room small = room("203", RoomType.DOUBLE);
        small.setCapacity(1);
        roomRepository.save(small);
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        assertThat(roomAssignmentService.getAvailableRooms(res.getId()))
                .extracting(RoomCandidateDto::roomNumber).containsExactly("201");
    }

    @Test
    void candidatesAreEmptyOnceReservationHasARoom() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        roomRepository.save(room("202", RoomType.DOUBLE));
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));
        roomAssignmentService.assignRoom(res.getId(), room201.getId());

        assertThat(roomAssignmentService.getAvailableRooms(res.getId())).isEmpty();
    }

    @Test
    void candidatesForUnknownReservationIsNotFound() {
        assertStatus(() -> roomAssignmentService.getAvailableRooms(999L), HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelledAssignmentDoesNotBlockRoom() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation first = reservationRepository.save(reservation("BKG-001", IN, OUT));
        RoomAssignment a = roomAssignmentService.assignRoom(first.getId(), room201.getId());
        a.setAssignmentStatus(AssignmentStatus.CANCELLED);
        roomAssignmentRepository.save(a);
        Reservation second = reservationRepository.save(reservation("BKG-002", IN, OUT));

        assertThat(roomAssignmentService.getAvailableRooms(second.getId()))
                .extracting(RoomCandidateDto::roomNumber).containsExactly("201");
    }

    @Test
    void assignCreatesAssignmentForStayDatesAndLogs() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        RoomAssignment a = roomAssignmentService.assignRoom(res.getId(), room201.getId());

        assertThat(a.getId()).isNotNull();
        assertThat(a.getRoom().getRoomNumber()).isEqualTo("201");
        assertThat(a.getAssignedCheckIn()).isEqualTo(IN);
        assertThat(a.getAssignedCheckOut()).isEqualTo(OUT);
        assertThat(a.getAssignmentStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-001"))
                .extracting(ReservationSyncLog::getEventType).containsExactly("ROOM_ASSIGNED");
    }

    @Test
    void assignRequiresPmsImport() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation pending = reservation("BKG-001", IN, OUT);
        pending.setPmsSyncStatus(PmsSyncStatus.PENDING_IMPORT);
        Reservation res = reservationRepository.save(pending);

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), room201.getId()), HttpStatus.CONFLICT);
        assertThat(roomAssignmentRepository.count()).isZero();
    }

    @Test
    void assignToWrongRoomTypeIsBadRequest() {
        Room twin = roomRepository.save(room("301", RoomType.TWIN));
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), twin.getId()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignToInactiveRoomIsBadRequest() {
        Room inactive = room("201", RoomType.DOUBLE);
        inactive.setStatus(RoomStatus.INACTIVE);
        Room saved = roomRepository.save(inactive);
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), saved.getId()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void assignToTooSmallRoomIsBadRequest() {
        Room small = room("201", RoomType.DOUBLE);
        small.setCapacity(1);
        Room saved = roomRepository.save(small);
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), saved.getId()), HttpStatus.BAD_REQUEST);
    }

    @Test
    void secondAssignmentForSameReservationIsConflict() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Room room202 = roomRepository.save(room("202", RoomType.DOUBLE));
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));
        roomAssignmentService.assignRoom(res.getId(), room201.getId());

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), room202.getId()), HttpStatus.CONFLICT);
    }

    @Test
    void assignUnknownRoomOrReservationIsNotFound() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation res = reservationRepository.save(reservation("BKG-001", IN, OUT));

        assertStatus(() -> roomAssignmentService.assignRoom(res.getId(), 999L), HttpStatus.NOT_FOUND);
        assertStatus(() -> roomAssignmentService.assignRoom(999L, room201.getId()), HttpStatus.NOT_FOUND);
    }

    @Test
    void assignedRoomNumbersAreMappedByReservationId() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation assigned = reservationRepository.save(reservation("BKG-001", IN, OUT));
        Reservation unassigned = reservationRepository.save(reservation("BKG-002", IN, OUT));
        roomAssignmentService.assignRoom(assigned.getId(), room201.getId());

        Map<Long, String> map = roomAssignmentService.getAssignedRoomNumbersByReservationId();

        assertThat(map).containsExactly(Map.entry(assigned.getId(), "201"));
        assertThat(map).doesNotContainKey(unassigned.getId());
    }

    @Disabled("FINDINGS #2: ACKNOWLEDGED 예약은 수동 배정이 409로 막힌다(자동 배정은 허용)")
    @Test
    void acknowledgedReservationCanBeAssignedManually() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation ack = reservation("BKG-001", IN, OUT);
        ack.setPmsSyncStatus(PmsSyncStatus.ACKNOWLEDGED);
        Reservation res = reservationRepository.save(ack);

        RoomAssignment a = roomAssignmentService.assignRoom(res.getId(), room201.getId());

        assertThat(a.getAssignmentStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
    }

    @Disabled("FINDINGS #5: 채널에서 취소된 예약의 객실 배정이 해제되지 않아 객실을 계속 점유한다")
    @Test
    void cancelledReservationNoLongerBlocksItsRoom() {
        Room room201 = roomRepository.save(room("201", RoomType.DOUBLE));
        Reservation cancelled = reservationRepository.save(reservation("BKG-001", IN, OUT));
        roomAssignmentService.assignRoom(cancelled.getId(), room201.getId());
        cancelled.setReservationStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(cancelled);
        Reservation next = reservationRepository.save(reservation("BKG-002", IN, OUT));

        assertThat(roomAssignmentService.getAvailableRooms(next.getId()))
                .extracting(RoomCandidateDto::roomNumber).containsExactly("201");
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(status);
    }

    private Reservation reservation(String externalReservationId, LocalDate checkIn, LocalDate checkOut) {
        Reservation reservation = new Reservation();
        reservation.setChannel(OtaChannel.BOOKING_COM);
        reservation.setExternalReservationId(externalReservationId);
        reservation.setGuestName("Guest " + externalReservationId);
        reservation.setCheckInDate(checkIn);
        reservation.setCheckOutDate(checkOut);
        reservation.setRoomType(RoomType.DOUBLE);
        reservation.setGuestCount(2);
        reservation.setPaymentStatus(PaymentStatus.PREPAID);
        reservation.setReservationStatus(ReservationStatus.CONFIRMED);
        reservation.setPmsSyncStatus(PmsSyncStatus.IMPORTED_TO_PMS);
        return reservation;
    }

    private Room room(String roomNumber, RoomType roomType) {
        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setRoomType(roomType);
        room.setCapacity(2);
        room.setStatus(RoomStatus.ACTIVE);
        return room;
    }
}
