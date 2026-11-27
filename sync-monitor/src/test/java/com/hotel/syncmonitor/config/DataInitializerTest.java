package com.hotel.syncmonitor.config;

import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static com.hotel.syncmonitor.Fixtures.doubleRoom;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false)
class DataInitializerTest {

    @Autowired private RoomRepository roomRepository;

    @Test
    void seedsTenActiveRoomsCoveringEveryRoomType() {
        new DataInitializer(roomRepository).run(new DefaultApplicationArguments());

        assertThat(roomRepository.count()).isEqualTo(10);
        assertThat(roomRepository.findAll()).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(RoomStatus.ACTIVE));
        assertThat(roomRepository.findAll()).extracting(Room::getRoomType).contains(RoomType.values());
        assertThat(roomRepository.findByRoomTypeAndStatus(RoomType.DOUBLE, RoomStatus.ACTIVE)).hasSize(3);
    }

    @Test
    void runningTwiceDoesNotDuplicate() {
        DataInitializer initializer = new DataInitializer(roomRepository);
        initializer.run(new DefaultApplicationArguments());
        initializer.run(new DefaultApplicationArguments());

        assertThat(roomRepository.count()).isEqualTo(10);
    }

    @Test
    void doesNotSeedWhenRoomsAlreadyExist() {
        roomRepository.save(doubleRoom("999"));

        new DataInitializer(roomRepository).run(new DefaultApplicationArguments());

        assertThat(roomRepository.count()).isEqualTo(1);
    }
}
