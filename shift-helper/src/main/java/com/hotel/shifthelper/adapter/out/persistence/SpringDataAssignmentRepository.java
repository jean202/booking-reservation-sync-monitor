package com.hotel.shifthelper.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface SpringDataAssignmentRepository extends JpaRepository<AssignmentJpaEntity, Long> {

    @Query("""
            select a from AssignmentJpaEntity a
            where a.checkOut > :ref
            """)
    List<AssignmentJpaEntity> findActiveOn(@Param("ref") LocalDate ref);

    Optional<AssignmentJpaEntity> findFirstByReservationId(Long reservationId);
}
