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
import org.junit.jupiter.api.Disabled;
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

    @Test
    void emptySystemReportsZeros() {
        DashboardDto dashboard = dashboardService.getDashboard();

        assertThat(dashboard).isEqualTo(new DashboardDto(0, 0, 0, 0, 0.0));
    }

    @Test
    void totalCountsEveryReservationRegardlessOfStatus() {
        Reservation cancelled = reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5);
        cancelled.setReservationStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(cancelled);
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.IMPORTED_TO_PMS, 5));

        DashboardDto dashboard = dashboardService.getDashboard();

        assertThat(dashboard.totalReservationCount()).isEqualTo(2);
        assertThat(dashboard.unassignedReservationCount()).isEqualTo(1);
    }

    @Test
    void unassignedConfirmedReservationWithNoFreeRoomIsOverbookingRisk() {
        Room room201 = roomRepository.save(room("201"));
        Reservation assigned = reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5));
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.IMPORTED_TO_PMS, 5));
        roomAssignmentService().assignRoom(assigned.getId(), room201.getId());

        DashboardDto dashboard = dashboardService.getDashboard();

        assertThat(dashboard.overbookingRiskCount()).isEqualTo(1);
        assertThat(dashboard.unassignedReservationCount()).isEqualTo(1);
    }

    @Test
    void noRiskWhileAFreeRoomRemains() {
        roomRepository.save(room("201"));
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5));

        assertThat(dashboardService.getDashboard().overbookingRiskCount()).isZero();
    }

    @Test
    void inactiveOrTooSmallRoomsDoNotCountAsAvailable() {
        Room inactive = room("201");
        inactive.setStatus(RoomStatus.INACTIVE);
        roomRepository.save(inactive);
        Room small = room("202");
        small.setCapacity(1);
        roomRepository.save(small);
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5));

        assertThat(dashboardService.getDashboard().overbookingRiskCount()).isEqualTo(1);
    }

    @Test
    void reservationWithNoRoomOfItsTypeIsRisk() {
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5));

        assertThat(dashboardService.getDashboard().overbookingRiskCount()).isEqualTo(1);
    }

    @Test
    void cancelledReservationIsNotRisk() {
        Reservation cancelled = reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5);
        cancelled.setReservationStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(cancelled);

        assertThat(dashboardService.getDashboard().overbookingRiskCount()).isZero();
    }

    @Test
    void averageDelayIgnoresMissingAndNegativeDelays() {
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 4));
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.IMPORTED_TO_PMS, -30));
        reservationRepository.save(reservation("BKG-003", PmsSyncStatus.PENDING_IMPORT, null));
        Reservation noEmail = reservation("BKG-004", PmsSyncStatus.IMPORTED_TO_PMS, 100);
        noEmail.setEmailReceivedAt(null);
        reservationRepository.save(noEmail);

        assertThat(dashboardService.getDashboard().averagePmsSyncDelayMinutes()).isEqualTo(4.0);
    }

    @Test
    void averageDelayIsFractional() {
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 3));
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.IMPORTED_TO_PMS, 4));

        assertThat(dashboardService.getDashboard().averagePmsSyncDelayMinutes()).isEqualTo(3.5);
    }

    @Test
    void onlyPendingImportCountsAsNotImported() {
        reservationRepository.save(reservation("BKG-001", PmsSyncStatus.PENDING_IMPORT, null));
        reservationRepository.save(reservation("BKG-002", PmsSyncStatus.FAILED, null));
        reservationRepository.save(reservation("BKG-003", PmsSyncStatus.ACKNOWLEDGED, 1));

        assertThat(dashboardService.getDashboard().notImportedToPmsCount()).isEqualTo(1);
    }

    @Disabled("FINDINGS #4: MODIFIED 상태 예약은 미배정·오버부킹 위험 집계에서 빠진다")
    @Test
    void modifiedReservationStillCountsAsUnassignedAndRisk() {
        Reservation modified = reservation("BKG-001", PmsSyncStatus.IMPORTED_TO_PMS, 5);
        modified.setReservationStatus(ReservationStatus.MODIFIED);
        reservationRepository.save(modified);

        DashboardDto dashboard = dashboardService.getDashboard();

        assertThat(dashboard.unassignedReservationCount()).isEqualTo(1);
        assertThat(dashboard.overbookingRiskCount()).isEqualTo(1);
    }

    private RoomAssignmentService roomAssignmentService() {
        return new RoomAssignmentService(reservationRepository, roomRepository,
                roomAssignmentRepository, new SyncLogService(syncLogRepository));
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
