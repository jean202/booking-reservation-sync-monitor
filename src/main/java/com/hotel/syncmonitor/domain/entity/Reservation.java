package com.hotel.syncmonitor.domain.entity;

import com.hotel.syncmonitor.domain.enums.*;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "reservations",
    // OTA reservation IDs are only unique inside their channel, so both fields define the idempotency key.
    uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "externalReservationId"})
)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OtaChannel channel;

    @Column(nullable = false)
    private String externalReservationId;

    @Column(nullable = false)
    private String guestName;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomType roomType;

    @Column(nullable = false)
    private int guestCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus reservationStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PmsSyncStatus pmsSyncStatus;

    private LocalDateTime emailReceivedAt;

    private LocalDateTime pmsImportedAt;

    public Reservation() {}

    public Long getId() { return id; }
    public OtaChannel getChannel() { return channel; }
    public String getExternalReservationId() { return externalReservationId; }
    public String getGuestName() { return guestName; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public RoomType getRoomType() { return roomType; }
    public int getGuestCount() { return guestCount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public ReservationStatus getReservationStatus() { return reservationStatus; }
    public PmsSyncStatus getPmsSyncStatus() { return pmsSyncStatus; }
    public LocalDateTime getEmailReceivedAt() { return emailReceivedAt; }
    public LocalDateTime getPmsImportedAt() { return pmsImportedAt; }

    public void setId(Long id) { this.id = id; }
    public void setChannel(OtaChannel channel) { this.channel = channel; }
    public void setExternalReservationId(String externalReservationId) { this.externalReservationId = externalReservationId; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }
    public void setGuestCount(int guestCount) { this.guestCount = guestCount; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setReservationStatus(ReservationStatus reservationStatus) { this.reservationStatus = reservationStatus; }
    public void setPmsSyncStatus(PmsSyncStatus pmsSyncStatus) { this.pmsSyncStatus = pmsSyncStatus; }
    public void setEmailReceivedAt(LocalDateTime emailReceivedAt) { this.emailReceivedAt = emailReceivedAt; }
    public void setPmsImportedAt(LocalDateTime pmsImportedAt) { this.pmsImportedAt = pmsImportedAt; }
}
