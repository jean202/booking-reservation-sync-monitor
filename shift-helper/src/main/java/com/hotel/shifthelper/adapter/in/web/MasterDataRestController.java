package com.hotel.shifthelper.adapter.in.web;

import com.hotel.shifthelper.application.MasterDataApplicationService;
import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shift")
class MasterDataRestController {

    private final MasterDataApplicationService service;

    MasterDataRestController(MasterDataApplicationService service) {
        this.service = service;
    }

    @PostMapping("/rooms")
    ResponseEntity<RoomView> registerRoom(@RequestBody RegisterRoomRequest req) {
        Room r = service.registerRoom(req.number(), req.capacity());
        return ResponseEntity.ok(RoomView.of(r));
    }

    @GetMapping("/rooms")
    List<RoomView> listRooms() {
        return service.listRooms().stream().map(RoomView::of).toList();
    }

    @PatchMapping("/rooms/{id}/service")
    RoomView toggle(@PathVariable Long id, @RequestBody ToggleServiceRequest req) {
        return RoomView.of(service.toggleRoomService(id, req.inService()));
    }

    @PostMapping("/reservations")
    ResponseEntity<ReservationView> createReservation(@RequestBody CreateReservationRequest req) {
        Reservation r = service.createReservation(
                req.guestName(), req.guestCount(), req.checkIn(), req.checkOut());
        return ResponseEntity.ok(ReservationView.of(r));
    }

    @GetMapping("/assignments")
    List<AssignmentView> listActiveAssignments() {
        return service.listActiveAssignments().stream().map(AssignmentView::of).toList();
    }

    record RegisterRoomRequest(String number, int capacity) {}
    record ToggleServiceRequest(boolean inService) {}
    record CreateReservationRequest(String guestName, int guestCount,
                                     LocalDate checkIn, LocalDate checkOut) {}

    record RoomView(Long id, String number, int capacity, boolean inService) {
        static RoomView of(Room r) {
            return new RoomView(r.id(), r.number(), r.capacity(), r.inService());
        }
    }
    record ReservationView(Long id, String guestName, int guestCount,
                            LocalDate checkIn, LocalDate checkOut) {
        static ReservationView of(Reservation r) {
            return new ReservationView(r.id(), r.guestName(), r.guestCount(),
                    r.checkIn(), r.checkOut());
        }
    }
    record AssignmentView(Long id, Long reservationId, Long roomId,
                           LocalDate checkIn, LocalDate checkOut) {
        static AssignmentView of(Assignment a) {
            return new AssignmentView(a.id(), a.reservationId(), a.roomId(),
                    a.checkIn(), a.checkOut());
        }
    }
}
