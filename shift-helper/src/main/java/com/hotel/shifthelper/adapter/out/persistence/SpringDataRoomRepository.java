package com.hotel.shifthelper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface SpringDataRoomRepository extends JpaRepository<RoomJpaEntity, Long> {
    List<RoomJpaEntity> findByInServiceTrue();
}
