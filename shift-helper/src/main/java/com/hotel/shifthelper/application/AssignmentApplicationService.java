package com.hotel.shifthelper.application;

import com.hotel.shifthelper.domain.AssignmentPlanner;
import com.hotel.shifthelper.domain.AssignmentPlanner.ActivePlacement;
import com.hotel.shifthelper.domain.model.Assignment;
import com.hotel.shifthelper.domain.model.AssignmentPlan;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Change;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Placement;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;
import com.hotel.shifthelper.domain.port.AssignmentRepository;
import com.hotel.shifthelper.domain.port.ReservationRepository;
import com.hotel.shifthelper.domain.port.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the preview/commit flow: loads master data via ports,
 * defers placement decisions to {@link AssignmentPlanner}, and persists
 * the resulting plan transactionally on commit.
 */
@Service
public class AssignmentApplicationService {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentPlanner planner;
    private final Clock clock;

    public AssignmentApplicationService(RoomRepository roomRepository,
                                        ReservationRepository reservationRepository,
                                        AssignmentRepository assignmentRepository,
                                        Clock clock) {
        this.roomRepository = roomRepository;
        this.reservationRepository = reservationRepository;
        this.assignmentRepository = assignmentRepository;
        this.planner = new AssignmentPlanner();
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AssignmentPlan preview(Long reservationId) {
        return computePlan(loadTarget(reservationId), LocalDate.now(clock));
    }

    @Transactional
    public AssignmentPlan commit(Long reservationId) {
        Reservation target = loadTarget(reservationId);
        LocalDate today = LocalDate.now(clock);
        AssignmentPlan plan = computePlan(target, today);
        if (!plan.feasible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, plan.message());
        }
        applyPlan(plan);
        return plan;
    }

    private Reservation loadTarget(Long reservationId) {
        Reservation target = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Reservation not found: " + reservationId));
        if (assignmentRepository.findByReservationId(reservationId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reservation already has an assignment.");
        }
        return target;
    }

    private AssignmentPlan computePlan(Reservation target, LocalDate today) {
        List<Room> rooms = roomRepository.findAllInService();
        List<Assignment> activeAssignments = assignmentRepository.findActiveOn(today);

        List<ActivePlacement> active = new ArrayList<>();
        for (Assignment a : activeAssignments) {
            Reservation r = reservationRepository.findById(a.reservationId()).orElse(null);
            if (r == null) continue;
            active.add(new ActivePlacement(r.id(), r.guestName(), r.guestCount(),
                    a.roomId(), a.checkIn(), a.checkOut()));
        }
        return planner.plan(target, rooms, active, today);
    }

    private void applyPlan(AssignmentPlan plan) {
        // Cache existing assignments by reservation id once, to avoid N lookups.
        Map<Long, Assignment> existing = new HashMap<>();
        for (Placement p : plan.placements()) {
            if (p.change() == Change.MOVED || p.change() == Change.LOCKED
                    || p.change() == Change.UNCHANGED) {
                assignmentRepository.findByReservationId(p.reservationId())
                        .ifPresent(a -> existing.put(p.reservationId(), a));
            }
        }

        for (Placement p : plan.placements()) {
            switch (p.change()) {
                case NEW -> {
                    Assignment a = new Assignment(null, p.reservationId(), p.roomId(),
                            p.checkIn(), p.checkOut());
                    assignmentRepository.save(a);
                }
                case MOVED -> {
                    Assignment a = existing.get(p.reservationId());
                    if (a != null) {
                        a.moveTo(p.roomId());
                        assignmentRepository.save(a);
                    }
                }
                case UNCHANGED, LOCKED -> { /* no-op */ }
            }
        }
    }
}
