package com.hotel.shifthelper.application;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterDataApplicationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 2);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private InMemoryPorts.Rooms rooms;
    private InMemoryPorts.Reservations reservations;
    private InMemoryPorts.Assignments assignments;
    private MasterDataApplicationService service;

    @BeforeEach
    void setUp() {
        rooms = new InMemoryPorts.Rooms();
        reservations = new InMemoryPorts.Reservations();
        assignments = new InMemoryPorts.Assignments();
        service = new MasterDataApplicationService(rooms, reservations, assignments,
                Clock.fixed(TODAY.atStartOfDay(ZONE).toInstant(), ZONE));
    }

    @Test
    void registeredRoomIsInServiceAndListed() {
        Room room = service.registerRoom("101", 2);

        assertThat(room.id()).isNotNull();
        assertThat(room.inService()).isTrue();
        assertThat(service.listRooms()).extracting(Room::number).containsExactly("101");
    }

    @Test
    void registerRejectsInvalidRoom() {
        assertThatThrownBy(() -> service.registerRoom("", 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registerRoom("101", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(rooms.store).isEmpty();
    }

    @Test
    void toggleServiceFlipsAndPersistsFlag() {
        Room room = service.registerRoom("101", 2);

        assertThat(service.toggleRoomService(room.id(), false).inService()).isFalse();
        assertThat(rooms.findById(room.id()).orElseThrow().inService()).isFalse();
        assertThat(service.toggleRoomService(room.id(), true).inService()).isTrue();
    }

    @Test
    void toggleUnknownRoomIsNotFound() {
        assertThatThrownBy(() -> service.toggleRoomService(77L, false))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createReservationValidatesAndPersists() {
        Reservation r = service.createReservation("Kim", 2, TODAY, TODAY.plusDays(1));

        assertThat(r.id()).isNotNull();
        assertThat(reservations.findById(r.id())).isPresent();
        assertThatThrownBy(() -> service.createReservation("Kim", 2, TODAY, TODAY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listActiveAssignmentsUsesClockTodayAndExcludesCheckedOut() {
        assignments.save(new Assignment(null, 1L, 1L, TODAY.minusDays(3), TODAY));
        assignments.save(new Assignment(null, 2L, 1L, TODAY.minusDays(1), TODAY.plusDays(1)));
        assignments.save(new Assignment(null, 3L, 1L, TODAY.plusDays(4), TODAY.plusDays(6)));

        assertThat(service.listActiveAssignments()).extracting(Assignment::reservationId)
                .containsExactlyInAnyOrder(2L, 3L);
    }
}
