package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.ReservationStatus;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.dto.DashboardDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class DashboardService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;

    public DashboardService(ReservationRepository reservationRepository,
                             RoomRepository roomRepository,
                             RoomAssignmentRepository roomAssignmentRepository) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.roomAssignmentRepository = roomAssignmentRepository;
    }

    public DashboardDto getDashboard() {
        long total = reservationRepository.count();
        long unassigned = countUnassigned();
        long notImportedToPms = reservationRepository.countByPmsSyncStatus(PmsSyncStatus.PENDING_IMPORT);
        long overbookingRisk = detectOverbookingRisk();
        double avgDelay = calculateAverageSyncDelay();

        return new DashboardDto(total, unassigned, notImportedToPms, overbookingRisk, avgDelay);
    }

    // CONFIRMED and MODIFIED are both live bookings that need a room; only CANCELLED is excluded.
    private List<Reservation> activeReservations() {
        return reservationRepository.findByReservationStatusNot(ReservationStatus.CANCELLED);
    }

    private long countUnassigned() {
        return activeReservations()
                .stream()
                .filter(r -> roomAssignmentRepository
                        .findByReservationIdAndAssignmentStatus(r.getId(), AssignmentStatus.ASSIGNED)
                        .isEmpty())
                .count();
    }

    private long detectOverbookingRisk() {
        return activeReservations()
                .stream()
                .filter(r -> {
                    boolean alreadyAssigned = !roomAssignmentRepository
                            .findByReservationIdAndAssignmentStatus(r.getId(), AssignmentStatus.ASSIGNED)
                            .isEmpty();
                    if (alreadyAssigned) return false;

                    long available = roomRepository
                            .findByRoomTypeAndStatus(r.getRoomType(), RoomStatus.ACTIVE)
                            .stream()
                            .filter(room -> roomCanHostReservation(room, r))
                            .filter(room -> roomAssignmentRepository
                                    .findOverlapping(room.getId(), r.getCheckInDate(), r.getCheckOutDate())
                                    .isEmpty())
                            .count();
                    return available == 0;
                })
                .count();
    }

    private boolean roomCanHostReservation(Room room, Reservation reservation) {
        return room.getCapacity() >= reservation.getGuestCount();
    }

    private double calculateAverageSyncDelay() {
        // Email and PMS timestamps expose invisible sync delays that hotel staff need to monitor.
        List<Long> delays = reservationRepository.findAll()
                .stream()
                .filter(r -> r.getEmailReceivedAt() != null && r.getPmsImportedAt() != null)
                .map(r -> Duration.between(r.getEmailReceivedAt(), r.getPmsImportedAt()).toMinutes())
                .filter(d -> d >= 0)
                .toList();

        if (delays.isEmpty()) return 0.0;
        return delays.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
}
