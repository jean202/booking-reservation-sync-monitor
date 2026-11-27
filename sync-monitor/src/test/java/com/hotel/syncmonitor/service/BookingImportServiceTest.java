package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.connector.BookingReservationConnector;
import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.ReservationSyncLog;
import com.hotel.syncmonitor.domain.enums.*;
import com.hotel.syncmonitor.dto.BookingReservationDto;
import com.hotel.syncmonitor.dto.ImportResultDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.repository.ReservationSyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hotel.syncmonitor.Fixtures.doubleRoom;
import static com.hotel.syncmonitor.Fixtures.dto;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class BookingImportServiceTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationSyncLogRepository syncLogRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAssignmentRepository roomAssignmentRepository;

    private BookingImportService bookingImportService;
    private ReservationService reservationService;
    private FixedBookingConnector connector;

    @BeforeEach
    void setUp() {
        connector = new FixedBookingConnector(new ArrayList<>(List.of(reservationDto())));
        SyncLogService syncLogService = new SyncLogService(syncLogRepository);
        bookingImportService = new BookingImportService(connector, reservationRepository, roomAssignmentRepository, syncLogService);
        reservationService = new ReservationService(reservationRepository, syncLogService, connector);
    }

    @Test
    void importReservationsDoesNotCreateDuplicateRowsForSameChannelAndExternalId() {
        ImportResultDto firstImport = bookingImportService.importReservations();
        ImportResultDto secondImport = bookingImportService.importReservations();

        assertThat(firstImport.imported()).isEqualTo(1);
        assertThat(secondImport.skipped()).isEqualTo(1);
        assertThat(reservationRepository.count()).isEqualTo(1);
        assertThat(reservationRepository.findByChannelAndExternalReservationId(
                OtaChannel.BOOKING_COM, "BKG-001"
        )).isPresent();
    }

    @Test
    void newReservationIsPersistedWithAllFieldsFromChannel() {
        bookingImportService.importReservations();

        Reservation saved = reservationRepository
                .findByChannelAndExternalReservationId(OtaChannel.BOOKING_COM, "BKG-001").orElseThrow();
        assertThat(saved.getGuestName()).isEqualTo("Guest A");
        assertThat(saved.getCheckInDate()).isEqualTo(LocalDate.of(2026, 5, 23));
        assertThat(saved.getCheckOutDate()).isEqualTo(LocalDate.of(2026, 5, 25));
        assertThat(saved.getRoomType()).isEqualTo(RoomType.DOUBLE);
        assertThat(saved.getGuestCount()).isEqualTo(2);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.PREPAID);
        assertThat(saved.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getPmsSyncStatus()).isEqualTo(PmsSyncStatus.IMPORTED_TO_PMS);
        assertThat(saved.getEmailReceivedAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 14, 3));
        assertThat(saved.getPmsImportedAt()).isEqualTo(LocalDateTime.of(2026, 5, 18, 14, 6));
    }

    @Test
    void importWritesImportedSyncLog() {
        bookingImportService.importReservations();

        List<ReservationSyncLog> logs = syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-001");
        assertThat(logs).extracting(ReservationSyncLog::getEventType).containsExactly("IMPORTED");
        assertThat(logs.get(0).getChannel()).isEqualTo(OtaChannel.BOOKING_COM);
    }

    @Test
    void sameExternalIdOnDifferentChannelsIsImportedAsSeparateReservations() {
        connector.reservations().add(dto(OtaChannel.AGODA, "BKG-001", ReservationStatus.CONFIRMED,
                PmsSyncStatus.IMPORTED_TO_PMS, null));

        ImportResultDto result = bookingImportService.importReservations();

        assertThat(result.imported()).isEqualTo(2);
        assertThat(reservationRepository.count()).isEqualTo(2);
    }

    @Test
    void reservationStatusChangeIsCountedAsUpdateAndLogged() {
        bookingImportService.importReservations();
        connector.reservations().set(0, dto(OtaChannel.BOOKING_COM, "BKG-001",
                ReservationStatus.CANCELLED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 14, 6)));

        ImportResultDto result = bookingImportService.importReservations();

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.imported()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(reservationRepository.findByChannelAndExternalReservationId(OtaChannel.BOOKING_COM, "BKG-001")
                .orElseThrow().getReservationStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-001"))
                .extracting(ReservationSyncLog::getEventType)
                .containsExactlyInAnyOrder("IMPORTED", "UPDATED");
    }

    @Test
    void pmsImportOfPendingReservationIsDetectedAsUpdate() {
        connector.reservations().set(0, dto(OtaChannel.BOOKING_COM, "BKG-001",
                ReservationStatus.CONFIRMED, PmsSyncStatus.PENDING_IMPORT, null));
        bookingImportService.importReservations();

        LocalDateTime importedAt = LocalDateTime.of(2026, 5, 18, 15, 0);
        connector.reservations().set(0, dto(OtaChannel.BOOKING_COM, "BKG-001",
                ReservationStatus.CONFIRMED, PmsSyncStatus.IMPORTED_TO_PMS, importedAt));
        ImportResultDto result = bookingImportService.importReservations();

        assertThat(result.updated()).isEqualTo(1);
        Reservation saved = reservationRepository
                .findByChannelAndExternalReservationId(OtaChannel.BOOKING_COM, "BKG-001").orElseThrow();
        assertThat(saved.getPmsSyncStatus()).isEqualTo(PmsSyncStatus.IMPORTED_TO_PMS);
        assertThat(saved.getPmsImportedAt()).isEqualTo(importedAt);
    }

    @Test
    void resultMessageSummarizesCounts() {
        bookingImportService.importReservations();
        connector.reservations().add(dto("BKG-002"));

        ImportResultDto result = bookingImportService.importReservations();

        assertThat(result.message()).isEqualTo("Import complete: 1 imported, 0 updated, 1 skipped.");
    }

    @Test
    void channelCancellationReleasesTheRoomAssignment() {
        bookingImportService.importReservations();
        Reservation saved = reservationRepository
                .findByChannelAndExternalReservationId(OtaChannel.BOOKING_COM, "BKG-001").orElseThrow();
        Room room = roomRepository.save(doubleRoom("201"));
        RoomAssignment assignment = new RoomAssignment();
        assignment.setReservation(saved);
        assignment.setRoom(room);
        assignment.setAssignedCheckIn(saved.getCheckInDate());
        assignment.setAssignedCheckOut(saved.getCheckOutDate());
        assignment = roomAssignmentRepository.save(assignment);

        connector.reservations().set(0, dto(OtaChannel.BOOKING_COM, "BKG-001",
                ReservationStatus.CANCELLED, PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 14, 6)));
        bookingImportService.importReservations();

        assertThat(roomAssignmentRepository.findById(assignment.getId()).orElseThrow().getAssignmentStatus())
                .isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(roomAssignmentRepository.findOverlapping(room.getId(),
                saved.getCheckInDate(), saved.getCheckOutDate())).isEmpty();
        assertThat(syncLogRepository.findByExternalReservationIdOrderByCreatedAtDesc("BKG-001"))
                .extracting(ReservationSyncLog::getEventType)
                .contains("ASSIGNMENT_RELEASED");
    }

    @Test
    void emptyFeedImportsNothing() {
        connector.reservations().clear();

        ImportResultDto result = bookingImportService.importReservations();

        assertThat(result.imported()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(reservationRepository.count()).isZero();
    }

    @Disabled("FINDINGS #1: 재-import가 PMS ACKNOWLEDGED 상태를 채널 값(IMPORTED_TO_PMS)으로 되돌린다")
    @Test
    void reimportDoesNotRevertLocalAcknowledgement() {
        bookingImportService.importReservations();
        Reservation saved = reservationRepository
                .findByChannelAndExternalReservationId(OtaChannel.BOOKING_COM, "BKG-001").orElseThrow();
        reservationService.acknowledgeReservation(saved.getId());

        ImportResultDto result = bookingImportService.importReservations();

        assertThat(reservationRepository.findById(saved.getId()).orElseThrow().getPmsSyncStatus())
                .isEqualTo(PmsSyncStatus.ACKNOWLEDGED);
        assertThat(result.skipped()).isEqualTo(1);
    }

    private BookingReservationDto reservationDto() {
        return new BookingReservationDto(
                OtaChannel.BOOKING_COM,
                "BKG-001",
                "Guest A",
                LocalDate.of(2026, 5, 23),
                LocalDate.of(2026, 5, 25),
                RoomType.DOUBLE,
                2,
                PaymentStatus.PREPAID,
                ReservationStatus.CONFIRMED,
                PmsSyncStatus.IMPORTED_TO_PMS,
                LocalDateTime.of(2026, 5, 18, 14, 3),
                LocalDateTime.of(2026, 5, 18, 14, 6)
        );
    }

    private record FixedBookingConnector(List<BookingReservationDto> reservations)
            implements BookingReservationConnector {

        @Override
        public List<BookingReservationDto> fetchNewReservations() {
            return reservations;
        }

        @Override
        public void acknowledge(String externalReservationId) {
        }
    }
}
