package com.hotel.shifthelper.domain;

import com.hotel.shifthelper.domain.model.AssignmentPlan;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Change;
import com.hotel.shifthelper.domain.model.AssignmentPlan.Placement;
import com.hotel.shifthelper.domain.model.Reservation;
import com.hotel.shifthelper.domain.model.Room;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure planner. Decides which in-service room each reservation (the new
 * target plus every currently pre-arrival placement) should occupy.
 *
 * <p>Algorithm: greedy constrained interval graph coloring.
 * Already-checked-in placements are locked to their room and block its
 * timeline. The movable pool — pre-arrival placements plus the target —
 * is re-placed in check-in order, picking the room whose most recent
 * prior checkout is closest to this check-in (= minimum gap), which
 * maximises back-to-back utilisation.
 */
public final class AssignmentPlanner {

    /**
     * Active placement currently held by a reservation, supplied to the planner
     * so it can decide whether to keep, move, or lock it.
     */
    public record ActivePlacement(
            Long reservationId,
            String guestName,
            int guestCount,
            Long currentRoomId,
            LocalDate checkIn,
            LocalDate checkOut
    ) {}

    public AssignmentPlan plan(Reservation target,
                                List<Room> rooms,
                                List<ActivePlacement> active,
                                LocalDate today) {

        if (rooms.isEmpty()) {
            return AssignmentPlan.infeasible(target.id(),
                    "No in-service rooms registered.");
        }

        Map<Long, Room> roomById = new HashMap<>();
        for (Room r : rooms) roomById.put(r.id(), r);

        // Split active placements into locked (already checked-in) and movable.
        Map<Long, List<Interval>> tracks = new HashMap<>();
        for (Room r : rooms) tracks.put(r.id(), new ArrayList<>());

        List<ActivePlacement> movable = new ArrayList<>();
        List<ActivePlacement> locked = new ArrayList<>();
        for (ActivePlacement p : active) {
            if (p.checkIn().isAfter(today)) {
                movable.add(p);
            } else {
                locked.add(p);
                tracks.computeIfAbsent(p.currentRoomId(), k -> new ArrayList<>())
                        .add(new Interval(p.checkIn(), p.checkOut()));
            }
        }

        // Pool: pre-arrival movables + target reservation.
        record PoolItem(Long reservationId, String guestName, int guestCount,
                         LocalDate checkIn, LocalDate checkOut, Long previousRoomId) {}
        List<PoolItem> pool = new ArrayList<>();
        for (ActivePlacement p : movable) {
            pool.add(new PoolItem(p.reservationId(), p.guestName(), p.guestCount(),
                    p.checkIn(), p.checkOut(), p.currentRoomId()));
        }
        pool.add(new PoolItem(target.id(), target.guestName(), target.guestCount(),
                target.checkIn(), target.checkOut(), null));
        pool.sort(Comparator.comparing(PoolItem::checkIn));

        // Greedy placement.
        Map<Long, Long> chosenRoom = new HashMap<>();
        for (PoolItem item : pool) {
            Room best = null;
            long bestGap = Long.MAX_VALUE;
            for (Room r : rooms) {
                if (r.capacity() < item.guestCount()) continue;
                List<Interval> timeline = tracks.get(r.id());
                if (overlapsAny(timeline, item.checkIn(), item.checkOut())) continue;
                long gap = gapBefore(timeline, item.checkIn());
                // Minimum gap maximises back-to-back utilisation; on a tie prefer
                // the tighter-capacity room so larger rooms stay free for larger
                // parties (best-fit), otherwise a feasible reshuffle can be missed.
                if (gap < bestGap
                        || (gap == bestGap && best != null
                            && r.capacity() < best.capacity())) {
                    bestGap = gap;
                    best = r;
                }
            }
            if (best == null) {
                return AssignmentPlan.infeasible(target.id(),
                        "No feasible placement even with reshuffle for guest "
                                + item.guestName() + ".");
            }
            chosenRoom.put(item.reservationId(), best.id());
            tracks.get(best.id()).add(new Interval(item.checkIn(), item.checkOut()));
        }

        // Build placements: NEW / MOVED / UNCHANGED for the movable pool,
        // LOCKED for already-checked-in placements (so the UI sees full state).
        List<Placement> placements = new ArrayList<>();
        List<Placement> moved = new ArrayList<>();

        for (PoolItem item : pool) {
            Long newRoomId = chosenRoom.get(item.reservationId());
            Room newRoom = roomById.get(newRoomId);
            Change change;
            String previousRoomNumber = null;
            if (item.previousRoomId() == null) {
                change = Change.NEW;
            } else if (!item.previousRoomId().equals(newRoomId)) {
                change = Change.MOVED;
                Room prev = roomById.get(item.previousRoomId());
                previousRoomNumber = prev != null ? prev.number() : null;
            } else {
                change = Change.UNCHANGED;
            }
            Placement p = new Placement(item.reservationId(), item.guestName(),
                    newRoomId, newRoom.number(),
                    item.checkIn(), item.checkOut(),
                    change, previousRoomNumber);
            placements.add(p);
            if (change == Change.MOVED) moved.add(p);
        }

        for (ActivePlacement p : locked) {
            Room r = roomById.get(p.currentRoomId());
            String roomNumber = r != null ? r.number() : "?";
            placements.add(new Placement(p.reservationId(), p.guestName(),
                    p.currentRoomId(), roomNumber,
                    p.checkIn(), p.checkOut(),
                    Change.LOCKED, null));
        }

        String message = moved.isEmpty()
                ? "Assignable without moving any pre-arrival guest."
                : "Assignable. " + moved.size() + " pre-arrival assignment(s) will move.";
        return new AssignmentPlan(true, target.id(), placements, moved, message);
    }

    private static boolean overlapsAny(List<Interval> timeline,
                                        LocalDate checkIn, LocalDate checkOut) {
        if (timeline == null) return false;
        for (Interval iv : timeline) {
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

    private record Interval(LocalDate checkIn, LocalDate checkOut) {}
}
