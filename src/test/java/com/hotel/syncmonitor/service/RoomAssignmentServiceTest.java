package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.RoomCandidateDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

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
