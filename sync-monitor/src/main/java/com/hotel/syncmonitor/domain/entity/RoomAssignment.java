package com.hotel.syncmonitor.domain.entity;

import com.hotel.syncmonitor.domain.enums.AssignmentStatus;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "room_assignments")
public class RoomAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private LocalDate assignedCheckIn;

    @Column(nullable = false)
    private LocalDate assignedCheckOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus assignmentStatus = AssignmentStatus.ASSIGNED;

    public RoomAssignment() {}

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public Room getRoom() { return room; }
    public LocalDate getAssignedCheckIn() { return assignedCheckIn; }
    public LocalDate getAssignedCheckOut() { return assignedCheckOut; }
    public AssignmentStatus getAssignmentStatus() { return assignmentStatus; }

    public void setId(Long id) { this.id = id; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }
    public void setRoom(Room room) { this.room = room; }
    public void setAssignedCheckIn(LocalDate assignedCheckIn) { this.assignedCheckIn = assignedCheckIn; }
    public void setAssignedCheckOut(LocalDate assignedCheckOut) { this.assignedCheckOut = assignedCheckOut; }
    public void setAssignmentStatus(AssignmentStatus assignmentStatus) { this.assignmentStatus = assignmentStatus; }
}
