package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Room;
import com.hotel.shifthelper.domain.port.RoomRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RoomPersistenceAdapter implements RoomRepository {

    private final SpringDataRoomRepository jpa;

    RoomPersistenceAdapter(SpringDataRoomRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Room> findById(Long id) {
        return jpa.findById(id).map(RoomJpaEntity::toDomain);
    }

    @Override
    public List<Room> findAllInService() {
        return jpa.findByInServiceTrue().stream().map(RoomJpaEntity::toDomain).toList();
    }

    @Override
    public List<Room> findAll() {
        return jpa.findAll().stream().map(RoomJpaEntity::toDomain).toList();
    }

    @Override
    public Room save(Room room) {
        return jpa.save(RoomJpaEntity.fromDomain(room)).toDomain();
    }
}
