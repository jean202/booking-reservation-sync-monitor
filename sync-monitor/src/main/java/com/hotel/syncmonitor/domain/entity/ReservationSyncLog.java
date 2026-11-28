package com.hotel.syncmonitor.domain.entity;

import com.hotel.syncmonitor.domain.enums.OtaChannel;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_sync_logs")
public class ReservationSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtaChannel channel;

    @Column(nullable = false)
    private String externalReservationId;

    @Column(nullable = false)
    private String eventType;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public ReservationSyncLog() {}

    public Long getId() { return id; }
    public OtaChannel getChannel() { return channel; }
    public String getExternalReservationId() { return externalReservationId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(Long id) { this.id = id; }
    public void setChannel(OtaChannel channel) { this.channel = channel; }
    public void setExternalReservationId(String externalReservationId) { this.externalReservationId = externalReservationId; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setMessage(String message) { this.message = message; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
