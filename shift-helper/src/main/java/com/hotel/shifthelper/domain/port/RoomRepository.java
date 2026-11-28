package com.hotel.shifthelper.domain.port;

import com.hotel.shifthelper.domain.model.Room;

import java.util.List;
import java.util.Optional;

/** Outbound port: persistence operations on Room aggregates. */
public interface RoomRepository {
    Optional<Room> findById(Long id);
    List<Room> findAllInService();
    List<Room> findAll();
    Room save(Room room);
}
