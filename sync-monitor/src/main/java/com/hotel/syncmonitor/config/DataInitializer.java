package com.hotel.syncmonitor.config;

import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataInitializer implements ApplicationRunner {

    private final RoomRepository roomRepository;

    public DataInitializer(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (roomRepository.count() > 0) return;

        roomRepository.saveAll(List.of(
            room("101", RoomType.SINGLE, 1),
            room("102", RoomType.SINGLE, 1),
            room("201", RoomType.DOUBLE, 2),
            room("202", RoomType.DOUBLE, 2),
            room("203", RoomType.DOUBLE, 2),
            room("301", RoomType.TWIN, 2),
            room("302", RoomType.TWIN, 2),
            room("401", RoomType.FAMILY, 4),
            room("402", RoomType.FAMILY, 4),
            room("501", RoomType.SUITE, 3)
        ));
    }

    private Room room(String number, RoomType type, int capacity) {
        Room r = new Room();
        r.setRoomNumber(number);
        r.setRoomType(type);
        r.setCapacity(capacity);
        r.setStatus(RoomStatus.ACTIVE);
        return r;
    }
}
