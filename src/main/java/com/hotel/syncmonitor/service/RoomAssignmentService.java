package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.dto.RoomCandidateDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class RoomAssignmentService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final SyncLogService syncLogService;

    public RoomAssignmentService(ReservationRepository reservationRepository,
                                  RoomRepository roomRepository,
                                  RoomAssignmentRepository roomAssignmentRepository,
                                  SyncLogService syncLogService) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.roomAssignmentRepository = roomAssignmentRepository;
        this.syncLogService = syncLogService;
    }

    public List<RoomCandidateDto> getAvailableRooms(Long reservationId) {
        Reservation reservation = findReservationOrThrow(reservationId);

        if (hasAssignedRoom(reservationId)) {
            return List.of();
        }

        return roomRepository.findByRoomTypeAndStatus(reservation.getRoomType(), RoomStatus.ACTIVE)
                .stream()
                .filter(room -> roomCanHostReservation(room, reservation))
                .filter(room -> isRoomAvailable(room.getId(), reservation))
                .map(room -> new RoomCandidateDto(room.getId(), room.getRoomNumber(), room.getRoomType(), room.getCapacity()))
                .toList();
    }

    @Transactional
    public RoomAssignment assignRoom(Long reservationId, Long roomId) {
        Reservation reservation = findReservationOrThrow(reservationId);
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found: " + roomId));

        if (reservation.getPmsSyncStatus() != PmsSyncStatus.IMPORTED_TO_PMS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room can only be assigned after PMS import. Current status: " + reservation.getPmsSyncStatus());
        }

        if (hasAssignedRoom(reservationId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reservation already has an assigned room.");
        }

        validateRoomCanHostReservation(room, reservation);

        if (!isRoomAvailable(roomId, reservation)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room " + room.getRoomNumber() + " is already assigned for this date range.");
        }

        RoomAssignment assignment = new RoomAssignment();
        assignment.setReservation(reservation);
        assignment.setRoom(room);
        assignment.setAssignedCheckIn(reservation.getCheckInDate());
        assignment.setAssignedCheckOut(reservation.getCheckOutDate());
        assignment.setAssignmentStatus(AssignmentStatus.ASSIGNED);
        RoomAssignment saved = roomAssignmentRepository.save(assignment);

        syncLogService.record(reservation.getChannel(), reservation.getExternalReservationId(),
                "ROOM_ASSIGNED", "Room " + room.getRoomNumber() + " assigned.");

        return saved;
    }

    public java.util.Map<Long, String> getAssignedRoomNumbersByReservationId() {
        return roomAssignmentRepository.findAssignedReservationIdAndRoomNumberPairs()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (String) row[1],
                        (a, b) -> a
                ));
    }

    public boolean isRoomAvailable(Long roomId, Reservation reservation) {
        return roomAssignmentRepository
                .findOverlapping(roomId, reservation.getCheckInDate(), reservation.getCheckOutDate())
                .isEmpty();
    }

    private boolean hasAssignedRoom(Long reservationId) {
        return !roomAssignmentRepository
                .findByReservationIdAndAssignmentStatus(reservationId, AssignmentStatus.ASSIGNED)
                .isEmpty();
    }

    private void validateRoomCanHostReservation(Room room, Reservation reservation) {
        if (!roomCanHostReservation(room, reservation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Room does not match the reservation room type, capacity, or active status.");
        }
    }

    private boolean roomCanHostReservation(Room room, Reservation reservation) {
        return room.getStatus() == RoomStatus.ACTIVE
                && room.getRoomType() == reservation.getRoomType()
                && room.getCapacity() >= reservation.getGuestCount();
    }

    private Reservation findReservationOrThrow(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reservation not found: " + reservationId));
    }
}
