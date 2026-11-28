package com.hotel.syncmonitor.domain.entity;

import com.hotel.syncmonitor.domain.enums.RoomStatus;
import com.hotel.syncmonitor.domain.enums.RoomType;
import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String roomNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.ACTIVE;

    public Room() {}

    public Room(Long id, String roomNumber, RoomType roomType, int capacity, RoomStatus status) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.roomType = roomType;
        this.capacity = capacity;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public RoomType getRoomType() { return roomType; }
    public int getCapacity() { return capacity; }
    public RoomStatus getStatus() { return status; }

    public void setId(Long id) { this.id = id; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public void setStatus(RoomStatus status) { this.status = status; }
}
