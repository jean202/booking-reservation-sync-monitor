package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.port.ReservationRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class ReservationPersistenceAdapter implements ReservationRepository {

    private final SpringDataReservationRepository jpa;

    ReservationPersistenceAdapter(SpringDataReservationRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Reservation> findById(Long id) {
        return jpa.findById(id).map(ReservationJpaEntity::toDomain);
    }

    @Override
    public Reservation save(Reservation reservation) {
        return jpa.save(ReservationJpaEntity.fromDomain(reservation)).toDomain();
    }
}
