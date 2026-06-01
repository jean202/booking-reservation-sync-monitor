package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.DashboardDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class DashboardServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                reservationRepository,
                roomRepository,
                roomAssignmentRepository
        );
    }

    @Test
    void getDashboardCountsPendingAndUnassignedReservationsAndAverageSyncDelay() {
        Room room201 = roomRepository.save(room("201"));
        roomRepository.save(room("202"));

        Reservation assigned = reservationRepository.save(reservation(
                "BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 10
        ));
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.PENDING_IMPORT, null));
        reservationRepository.save(reservation("BKG-003", PmsSyncStatus.IMPORTED_TO_PMS, 20));

        RoomAssignmentService roomAssignmentService = new RoomAssignmentService(
                reservationRepository,
                roomRepository,
                roomAssignmentRepository,
                new SyncLogService(syncLogRepository)
        );
        roomAssignmentService.assignRoom(assigned.getId(), room201.getId());

        DashboardDto dashboard = dashboardService.getDashboard();

        assertThat(dashboard.notImportedToPmsCount()).isEqualTo(1);
        assertThat(dashboard.unassignedReservationCount()).isEqualTo(2);
        assertThat(dashboard.averagePmsSyncDelayMinutes()).isEqualTo(15.0);
    }

    private Reservation reservation(String externalReservationId,
                                    PmsSyncStatus pmsSyncStatus,
                                    Integer syncDelayMinutes) {
        Reservation reservation = new Reservation();
        reservation.setChannel(OtaChannel.BOOKING_COM);
        reservation.setExternalReservationId(externalReservationId);
        reservation.setGuestName("Guest " + externalReservationId);
        reservation.setCheckInDate(LocalDate.of(2026, 5, 23));
        reservation.setCheckOutDate(LocalDate.of(2026, 5, 25));
        reservation.setRoomType(RoomType.DOUBLE);
        reservation.setGuestCount(2);
        reservation.setPaymentStatus(PaymentStatus.PREPAID);
        reservation.setReservationStatus(ReservationStatus.CONFIRMED);
        reservation.setPmsSyncStatus(pmsSyncStatus);
        reservation.setEmailReceivedAt(LocalDateTime.of(2026, 5, 18, 14, 0));
        if (syncDelayMinutes != null) {
            reservation.setPmsImportedAt(reservation.getEmailReceivedAt().plusMinutes(syncDelayMinutes));
        }
        return reservation;
    }

    private Room room(String roomNumber) {
        Room room = new Room();
        room.setRoomNumber(roomNumber);
        room.setRoomType(RoomType.DOUBLE);
        room.setCapacity(2);
        room.setStatus(RoomStatus.ACTIVE);
        return room;
    }
}
