package com.hotel.shifthelper.application;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import com.hotel.shifthelper.domain.port.AssignmentRepository;
import com.hotel.shifthelper.domain.port.ReservationRepository;
import com.hotel.shifthelper.domain.port.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Master-data commands: register the room inventory once at the start
 * of a shift, toggle the in-service flag, create reservations, and list.
 */
@Service
public class MasterDataApplicationService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final AssignmentRepository assignmentRepository;
    private final Clock clock;

    public MasterDataApplicationService(RoomRepository roomRepository,
                                        ReservationRepository reservationRepository,
                                        AssignmentRepository assignmentRepository,
                                        Clock clock) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.assignmentRepository = assignmentRepository;
        this.clock = clock;
    }

    @Transactional
    public Room registerRoom(String number, int capacity) {
        return roomRepository.save(new Room(null, number, capacity, true));
    }

    @Transactional
    public Room toggleRoomService(Long roomId, boolean inService) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Room not found: " + roomId));
        if (inService) room.markInService(); else room.markOutOfService();
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<Room> listRooms() {
        return roomRepository.findAll();
    }

    @Transactional
    public Reservation createReservation(String guestName, int guestCount,
                                          LocalDate checkIn, LocalDate checkOut) {
        return reservationRepository.save(
                new Reservation(null, guestName, guestCount, checkIn, checkOut));
    }

    @Transactional(readOnly = true)
    public List<Assignment> listActiveAssignments() {
        return assignmentRepository.findActiveOn(LocalDate.now(clock));
    }
}
