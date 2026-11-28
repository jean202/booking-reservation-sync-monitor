package com.hotel.shifthelper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReservationRepository extends JpaRepository<ReservationJpaEntity, Long> {
}
