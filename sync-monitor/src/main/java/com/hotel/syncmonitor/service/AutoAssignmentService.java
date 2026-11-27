package com.hotel.syncmonitor.service;

import com.hotel.syncmonitor.domain.entity.Reservation;
import com.hotel.syncmonitor.domain.entity.Room;
import com.hotel.syncmonitor.domain.entity.RoomAssignment;
import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import com.hotel.syncmonitor.domain.enums.PmsSyncStatus;
import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.dto.AssignmentPlanDto;
import com.hotel.syncmonitor.repository.ReservationRepository;
import com.hotel.syncmonitor.repository.RoomAssignmentRepository;
import com.hotel.syncmonitor.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auto-assigns a room to a reservation, reshuffling pre-arrival assignments
 * within the same room type when necessary so that every guest stays in a
 * single room from check-in to check-out.
 *
 * <p>Algorithm: Constrained Interval Graph Coloring (greedy).
 * <ul>
 *   <li>Each room of a given type is a "track" (timeline of intervals).</li>
 *   <li>Already-checked-in assignments are <b>locked</b> to their current room.</li>
 *   <li>Pre-arrival assignments + the target reservation form the
 *       <b>movable pool</b>. If the target fits while every pre-arrival guest
 *       keeps their room, nobody moves; otherwise the pool is re-placed greedily.</li>
 *   <li>For each reservation (sorted by check-in date) we pick the room whose
 *       most recent occupied checkout is closest to this check-in
 *       (= minimum gap), maximizing back-to-back utilization.</li>
 * </ul>
 *
 * <p>Time complexity: O(N · R) where N = reservations of this type and
 * R = rooms of this type. For hotel-scale data (hundreds), this is trivial.</p>
 */
@Service
public class AutoAssignmentService {

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final SyncLogService syncLogService;

    public AutoAssignmentService(ReservationRepository reservationRepository,
                                  RoomRepository roomRepository,
                                  RoomAssignmentRepository roomAssignmentRepository,
                                  SyncLogService syncLogService) {
        this.reservationRepository = reservationRepository;
        this.roomRepository = roomRepository;
        this.roomAssignmentRepository = roomAssignmentRepository;
        this.syncLogService = syncLogService;
    }

    /** Compute a plan without persisting anything. */
    @Transactional(readOnly = true)
    public AssignmentPlanDto preview(Long reservationId) {
        Reservation target = loadAndValidate(reservationId);
        return computePlan(target, LocalDate.now());
    }

    /** Compute a plan and, if feasible, persist all changes atomically. */
    @Transactional
    public AssignmentPlanDto commit(Long reservationId) {
        Reservation target = loadAndValidate(reservationId);
        LocalDate today = LocalDate.now();
        AssignmentPlanDto plan = computePlan(target, today);

        if (!plan.feasible()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, plan.message());
        }

        applyPlan(target, plan, today);
        syncLogService.record(target.getChannel(), target.getExternalReservationId(),
                "AUTO_ASSIGNED",
                "Auto-assigned via reshuffle. Moved " + plan.movedPlacements().size()
                        + " existing assignment(s).");
        return plan;
    }

    // ---------- internals ----------

    private Reservation loadAndValidate(Long reservationId) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Reservation not found: " + reservationId));

        if (r.getPmsSyncStatus() != PmsSyncStatus.IMPORTED_TO_PMS
                && r.getPmsSyncStatus() != PmsSyncStatus.ACKNOWLEDGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Auto-assign requires PMS import. Current status: " + r.getPmsSyncStatus());
        }

        boolean alreadyAssigned = !roomAssignmentRepository
                .findByReservationIdAndAssignmentStatus(reservationId, AssignmentStatus.ASSIGNED)
                .isEmpty();
        if (alreadyAssigned) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Reservation already has an assigned room.");
        }
        return r;
    }

    private AssignmentPlanDto computePlan(Reservation target, LocalDate today) {
        // All active rooms of the type are candidates; capacity is checked per
        // reservation during placement, since moved guests have their own party size.
        List<Room> rooms = roomRepository.findByRoomTypeAndStatus(target.getRoomType(), RoomStatus.ACTIVE);

        if (rooms.stream().noneMatch(rm -> rm.getCapacity() >= target.getGuestCount())) {
            return new AssignmentPlanDto(false, target.getId(), target.getRoomType(),
                    List.of(), List.of(),
                    "No active rooms of type " + target.getRoomType()
                            + " with capacity ≥ " + target.getGuestCount() + ".");
        }

        List<RoomAssignment> active = roomAssignmentRepository
                .findActiveByRoomType(target.getRoomType(), today);

        // 1) Lock already-checked-in assignments to their current rooms.
        Map<Long, List<Interval>> tracks = new HashMap<>();
        for (Room rm : rooms) tracks.put(rm.getId(), new ArrayList<>());

        List<RoomAssignment> movable = new ArrayList<>();
        for (RoomAssignment a : active) {
            if (a.getAssignedCheckIn().isAfter(today)) {
                movable.add(a);
            } else {
                // locked: occupant already in the room
                tracks.computeIfAbsent(a.getRoom().getId(), k -> new ArrayList<>())
                        .add(Interval.locked(a));
            }
        }

        // 2) Pool = pre-arrival movable + target. Sort by check-in date.
        List<Item> pool = new ArrayList<>();
        for (RoomAssignment a : movable) {
            pool.add(new Item(a.getReservation(), a));
        }
        pool.add(new Item(target, null));
        pool.sort(Comparator.comparing(it -> it.reservation().getCheckInDate()));

        // 3) First try to fit the target while every pre-arrival guest keeps
        //    their room; reshuffle only when that is impossible.
        Map<Long, Long> assignedRoomByReservation = placeKeepingCurrentRooms(pool, rooms, copy(tracks));
        if (assignedRoomByReservation == null) {
            // Greedy reshuffle: prefer the room whose most recent prior checkout
            // is closest to this check-in (minimum gap → maximum utilization).
            assignedRoomByReservation = new HashMap<>();
            for (Item it : pool) {
                Reservation r = it.reservation();
                Room best = pickRoom(r, rooms, tracks);
                if (best == null) {
                    return new AssignmentPlanDto(false, target.getId(), target.getRoomType(),
                            List.of(), List.of(),
                            "No feasible placement even with reshuffle for reservation "
                                    + r.getExternalReservationId() + ".");
                }
                assignedRoomByReservation.put(r.getId(), best.getId());
                tracks.get(best.getId()).add(Interval.proposed(r));
            }
        }

        // 4) Build the plan: classify each placement as NEW / MOVED / UNCHANGED.
        Map<Long, Room> roomById = new HashMap<>();
        for (Room rm : rooms) roomById.put(rm.getId(), rm);

        List<AssignmentPlanDto.Placement> placements = new ArrayList<>();
        List<AssignmentPlanDto.Placement> moved = new ArrayList<>();

        for (Item it : pool) {
            Reservation r = it.reservation();
            Long newRoomId = assignedRoomByReservation.get(r.getId());
            Room newRoom = roomById.get(newRoomId);

            String change;
            String previousRoomNumber = null;
            if (it.existing() == null) {
                change = "NEW";
            } else if (!it.existing().getRoom().getId().equals(newRoomId)) {
                change = "MOVED";
                previousRoomNumber = it.existing().getRoom().getRoomNumber();
            } else {
                change = "UNCHANGED";
            }

            AssignmentPlanDto.Placement p = new AssignmentPlanDto.Placement(
                    r.getId(), r.getExternalReservationId(), r.getGuestName(),
                    newRoom.getId(), newRoom.getRoomNumber(),
                    r.getCheckInDate(), r.getCheckOutDate(),
                    change, previousRoomNumber
            );
            placements.add(p);
            if ("MOVED".equals(change)) moved.add(p);
        }

        // Also emit locked (already checked-in) placements so the UI can show full state.
        for (RoomAssignment a : active) {
            if (a.getAssignedCheckIn().isAfter(today)) continue;
            Room rm = a.getRoom();
            placements.add(new AssignmentPlanDto.Placement(
                    a.getReservation().getId(),
                    a.getReservation().getExternalReservationId(),
                    a.getReservation().getGuestName(),
                    rm.getId(), rm.getRoomNumber(),
                    a.getAssignedCheckIn(), a.getAssignedCheckOut(),
                    "LOCKED", null
            ));
        }

        return new AssignmentPlanDto(true, target.getId(), target.getRoomType(),
                placements, moved,
                moved.isEmpty()
                        ? "Assignable without moving anyone."
                        : "Assignable. " + moved.size() + " pre-arrival assignment(s) will move.");
    }

    private void applyPlan(Reservation target, AssignmentPlanDto plan, LocalDate today) {
        Map<Long, Room> roomById = new HashMap<>();
        for (Room rm : roomRepository.findByRoomTypeAndStatus(target.getRoomType(), RoomStatus.ACTIVE)) {
            roomById.put(rm.getId(), rm);
        }

        // Existing pre-arrival assignments keyed by reservation id.
        Map<Long, RoomAssignment> existingByReservation = new HashMap<>();
        for (RoomAssignment a : roomAssignmentRepository.findActiveByRoomType(target.getRoomType(), today)) {
            if (a.getAssignedCheckIn().isAfter(today)) {
                existingByReservation.put(a.getReservation().getId(), a);
            }
        }

        for (AssignmentPlanDto.Placement p : plan.placements()) {
            if ("LOCKED".equals(p.change()) || "UNCHANGED".equals(p.change())) continue;

            if ("MOVED".equals(p.change())) {
                RoomAssignment a = existingByReservation.get(p.reservationId());
                if (a == null) continue; // defensive
                a.setRoom(roomById.get(p.roomId()));
                roomAssignmentRepository.save(a);
            } else if ("NEW".equals(p.change())) {
                RoomAssignment a = new RoomAssignment();
                a.setReservation(target);
                a.setRoom(roomById.get(p.roomId()));
                a.setAssignedCheckIn(p.checkInDate());
                a.setAssignedCheckOut(p.checkOutDate());
                a.setAssignmentStatus(AssignmentStatus.ASSIGNED);
                roomAssignmentRepository.save(a);
            }
        }
    }

    // ----- timeline helpers -----

    private record Item(Reservation reservation, RoomAssignment existing) {}

    /** Returns null when some pre-arrival guest cannot stay or the target does not fit. */
    private static Map<Long, Long> placeKeepingCurrentRooms(List<Item> pool, List<Room> rooms,
                                                            Map<Long, List<Interval>> tracks) {
        Map<Long, Long> chosen = new HashMap<>();
        for (Item it : pool) {
            if (it.existing() == null) continue;
            Reservation r = it.reservation();
            Room current = it.existing().getRoom();
            List<Interval> timeline = tracks.get(current.getId());
            if (timeline == null || current.getCapacity() < r.getGuestCount()
                    || overlapsAny(timeline, r.getCheckInDate(), r.getCheckOutDate())) {
                return null;
            }
            chosen.put(r.getId(), current.getId());
            timeline.add(Interval.proposed(r));
        }
        for (Item it : pool) {
            if (it.existing() != null) continue;
            Reservation r = it.reservation();
            Room best = pickRoom(r, rooms, tracks);
            if (best == null) return null;
            chosen.put(r.getId(), best.getId());
            tracks.get(best.getId()).add(Interval.proposed(r));
        }
        return chosen;
    }

    private static Room pickRoom(Reservation r, List<Room> rooms, Map<Long, List<Interval>> tracks) {
        Room best = null;
        long bestGap = Long.MAX_VALUE;
        for (Room rm : rooms) {
            if (rm.getCapacity() < r.getGuestCount()) continue;
            List<Interval> timeline = tracks.get(rm.getId());
            if (overlapsAny(timeline, r.getCheckInDate(), r.getCheckOutDate())) continue;
            long gap = gapBefore(timeline, r.getCheckInDate());
            if (gap < bestGap) {
                bestGap = gap;
                best = rm;
            }
        }
        return best;
    }

    private static Map<Long, List<Interval>> copy(Map<Long, List<Interval>> tracks) {
        Map<Long, List<Interval>> copy = new HashMap<>();
        tracks.forEach((roomId, timeline) -> copy.put(roomId, new ArrayList<>(timeline)));
        return copy;
    }

    private static boolean overlapsAny(List<Interval> timeline, LocalDate checkIn, LocalDate checkOut) {
        if (timeline == null) return false;
        for (Interval iv : timeline) {
            // back-to-back (checkout == next checkin) does NOT overlap
            if (iv.checkIn().isBefore(checkOut) && iv.checkOut().isAfter(checkIn)) return true;
        }
        return false;
    }

    private static long gapBefore(List<Interval> timeline, LocalDate checkIn) {
        if (timeline == null || timeline.isEmpty()) return Long.MAX_VALUE / 2;
        long best = Long.MAX_VALUE / 2;
        for (Interval iv : timeline) {
            if (!iv.checkOut().isAfter(checkIn)) {
                long gap = checkIn.toEpochDay() - iv.checkOut().toEpochDay();
                if (gap < best) best = gap;
            }
        }
        return best;
    }

    private record Interval(LocalDate checkIn, LocalDate checkOut, boolean locked) {
        static Interval locked(RoomAssignment a) {
            return new Interval(a.getAssignedCheckIn(), a.getAssignedCheckOut(), true);
        }
        static Interval proposed(Reservation r) {
            return new Interval(r.getCheckInDate(), r.getCheckOutDate(), false);
        }
    }
}
