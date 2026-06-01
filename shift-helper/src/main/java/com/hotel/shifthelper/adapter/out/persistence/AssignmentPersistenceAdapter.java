package com.hotel.shifthelper.adapter.out.persistence;

import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.port.AssignmentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
class AssignmentPersistenceAdapter implements AssignmentRepository {

    private final SpringDataAssignmentRepository jpa;

    AssignmentPersistenceAdapter(SpringDataAssignmentRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Assignment> findById(Long id) {
        return jpa.findById(id).map(AssignmentJpaEntity::toDomain);
    }

    @Override
    public List<Assignment> findActiveOn(LocalDate referenceDate) {
        return jpa.findActiveOn(referenceDate).stream()
                .map(AssignmentJpaEntity::toDomain).toList();
    }

    @Override
    public Optional<Assignment> findByReservationId(Long reservationId) {
        return jpa.findFirstByReservationId(reservationId).map(AssignmentJpaEntity::toDomain);
    }

    @Override
    public Assignment save(Assignment assignment) {
        return jpa.save(AssignmentJpaEntity.fromDomain(assignment)).toDomain();
    }
}
