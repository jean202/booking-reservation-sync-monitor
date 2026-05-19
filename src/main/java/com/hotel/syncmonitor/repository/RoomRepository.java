package com.hotel.syncmonitor.repository;

import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByRoomTypeAndStatus(RoomType roomType, RoomStatus status);

    List<Room> findByStatus(RoomStatus status);
}
