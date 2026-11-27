package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.connector.BookingReservationConnector;
import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.ReservationStatus;
import com.hotel.syncmonitor.dto.BookingReservationDto;
import com.hotel.syncmonitor.dto.ImportResultDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class BookingImportService {

    private final BookingReservationConnector connector;
    private final ReservationRepository reservationRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final SyncLogService syncLogService;

    public BookingImportService(BookingReservationConnector connector,
                                 ReservationRepository reservationRepository,
                                 RoomAssignmentRepository roomAssignmentRepository,
                                 SyncLogService syncLogService) {
        this.connector = connector;
        this.reservationRepository = reservationRepository;
        this.roomAssignmentRepository = roomAssignmentRepository;
        this.syncLogService = syncLogService;
    }

    @Transactional
    public ImportResultDto importReservations() {
        List<BookingReservationDto> incoming = connector.fetchNewReservations();

        int imported = 0;
        int updated = 0;
        int skipped = 0;

        for (BookingReservationDto dto : incoming) {
            Optional<Reservation> existing = reservationRepository
                    .findByChannelAndExternalReservationId(dto.channel(), dto.externalReservationId());

            if (existing.isEmpty()) {
                Reservation reservation = new Reservation();
                reservation.setChannel(dto.channel());
                reservation.setExternalReservationId(dto.externalReservationId());
                reservation.setGuestName(dto.guestName());
                reservation.setCheckInDate(dto.checkInDate());
                reservation.setCheckOutDate(dto.checkOutDate());
                reservation.setRoomType(dto.roomType());
                reservation.setGuestCount(dto.guestCount());
                reservation.setPaymentStatus(dto.paymentStatus());
                reservation.setReservationStatus(dto.reservationStatus());
                reservation.setPmsSyncStatus(dto.pmsSyncStatus());
                reservation.setEmailReceivedAt(dto.emailReceivedAt());
                reservation.setPmsImportedAt(dto.pmsImportedAt());
                reservationRepository.save(reservation);
                syncLogService.record(dto.channel(), dto.externalReservationId(),
                        "IMPORTED", "Reservation imported from channel.");
                imported++;

            } else {
                Reservation res = existing.get();
                boolean wasCancelled = res.getReservationStatus() == ReservationStatus.CANCELLED;
                boolean changed = applyStatusUpdates(dto, res);

                if (changed) {
                    reservationRepository.save(res);
                    syncLogService.record(dto.channel(), dto.externalReservationId(),
                            "UPDATED", "Reservation status updated.");
                    if (!wasCancelled && res.getReservationStatus() == ReservationStatus.CANCELLED) {
                        releaseAssignments(res);
                    }
                    updated++;
                } else {
                    skipped++;
                }
            }
        }

        return new ImportResultDto(
                imported, updated, skipped,
                String.format("Import complete: %d imported, %d updated, %d skipped.", imported, updated, skipped)
        );
    }

    private void releaseAssignments(Reservation reservation) {
        List<RoomAssignment> assigned = roomAssignmentRepository
                .findByReservationIdAndAssignmentStatus(reservation.getId(), AssignmentStatus.ASSIGNED);
        for (RoomAssignment a : assigned) {
            a.setAssignmentStatus(AssignmentStatus.CANCELLED);
            roomAssignmentRepository.save(a);
            syncLogService.record(reservation.getChannel(), reservation.getExternalReservationId(),
                    "ASSIGNMENT_RELEASED",
                    "Room " + a.getRoom().getRoomNumber() + " released because the reservation was cancelled.");
        }
    }

    private boolean applyStatusUpdates(BookingReservationDto dto, Reservation reservation) {
        boolean changed = false;

        if (reservation.getReservationStatus() != dto.reservationStatus()) {
            reservation.setReservationStatus(dto.reservationStatus());
            changed = true;
        }
        if (reservation.getPmsSyncStatus() != dto.pmsSyncStatus()) {
            reservation.setPmsSyncStatus(dto.pmsSyncStatus());
            changed = true;
        }
        if (!Objects.equals(reservation.getPmsImportedAt(), dto.pmsImportedAt())) {
            reservation.setPmsImportedAt(dto.pmsImportedAt());
            changed = true;
        }

        return changed;
    }
}
