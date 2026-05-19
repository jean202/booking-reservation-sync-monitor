# Booking Reservation Sync Monitor

A Java 17 + Spring Boot 3 backend MVP that models real-world OTA reservation synchronization — including PMS import delay detection, room assignment recommendation, and operational dashboard monitoring.

> **Disclaimer:** This project does **not** call the real Booking.com API. It is inspired by the [Booking.com Reservations API](https://developers.booking.com/api/index.html) retrieve/acknowledge flow. Actual API access requires official Booking.com Connectivity Partner authorization. All data is mock-only. No real customer names, reservation numbers, payment data, or hotel credentials are used.

---

## Background

I work part-time at a hotel on weekends. The hotel receives reservations through Booking.com, Agoda, Trip.com, and Airbnb. I noticed that reservation emails from Booking.com sometimes arrive minutes before the PMS (Property Management System) reflects them. This lag — if untracked — can lead to missed room preparation and undetected double bookings.

This project turns that observation into a backend system: a monitor that tracks whether OTA reservations have been synced to the PMS, flags delays, prevents duplicate imports, recommends available rooms, and exposes a dashboard for operations staff.

---

## Tech Stack

| | |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Persistence | Spring Data JPA · H2 demo database · MySQL-compatible JPA mapping |
| API Documentation | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |

Schema design uses standard column types, `@UniqueConstraint`, and `@Enumerated(STRING)`, so the entity model is intended to be portable to MySQL. This MVP ships with H2 only; a real MySQL profile and driver are listed as future work.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      REST Controllers                         │
│   MockBookingReservationController · ReservationController   │
│   DashboardController                                       │
└──────────────────────────────┬───────────────────────────────┘
                               │
┌──────────────────────────────▼───────────────────────────────┐
│                         Services                              │
│   BookingImportService · ReservationService ·                │
│   RoomAssignmentService · DashboardService                   │
└──────────┬───────────────────────────────────────────────────┘
           │
┌──────────▼──────────┐   ┌──────────────────────────────────┐
│  BookingReservation  │   │           Repositories            │
│  Connector           │   │  Reservation · Room ·             │
│  (interface)         │   │  RoomAssignment · SyncLog         │
│  └─ MockImpl         │   └──────────────────────────────────┘
└─────────────────────┘                    │
                                  ┌────────▼───────┐
                                  │ H2 DB / MySQL  │
                                  │ compatible map │
                                  └────────────────┘
```

**Key design principle:** The `BookingReservationConnector` interface decouples OTA integration from business logic. Swapping the mock for a real HTTP client requires zero changes to the service layer.

---

## Package Structure

| Package | Responsibility |
|---|---|
| `controller` | REST endpoints, response DTO mapping, API error handling |
| `service` | Business rules for import, acknowledgement, room assignment, and dashboard metrics |
| `repository` | Spring Data JPA persistence access |
| `connector` | OTA integration boundary; currently a mock Booking.com connector only |
| `domain.entity` | JPA entities persisted to H2 |
| `domain.enums` | Shared domain states such as OTA channel, reservation status, and room type |
| `dto` | API response records and connector payload records |

---

## Core Features

### 1. Mock Booking.com Connector
Models the Booking.com retrieve/acknowledge API flow. The `MockBookingReservationConnector` returns hardcoded reservations through the `BookingReservationConnector` interface; a real implementation would call the Booking.com Connectivity API and require partner credentials.

### 2. Idempotent Reservation Import
Reservations are deduplicated by `(channel, externalReservationId)`. Calling the import endpoint multiple times is safe — already-existing records are updated only if status fields or the PMS import timestamp changed, otherwise skipped.

### 3. PMS Sync Delay Detection
Each reservation tracks `emailReceivedAt` and `pmsImportedAt`. The dashboard reports `notImportedToPmsCount` (reservations still at `PENDING_IMPORT`) and `averagePmsSyncDelayMinutes`. In production, this feeds alert thresholds.

### 4. Room Assignment Recommendation
Given a reservation, the system returns only active rooms that match the required `roomType`, can hold the guest count, and have no overlapping `ASSIGNED` assignment for the requested dates. Overlap logic: `checkIn < existingCheckOut AND existingCheckIn < checkOut`.

### 5. Overbooking Risk Detection
Identifies confirmed reservations that have no room assignment **and** no available active room of the required type and capacity — meaning the hotel is at capacity for those dates.

### 6. Acknowledge Simulation
`POST /api/reservations/{id}/acknowledge` calls the connector acknowledge hook, sets `pmsSyncStatus` to `ACKNOWLEDGED`, and writes a `ReservationSyncLog` entry — mirroring the Booking.com acknowledge pattern that confirms a reservation has been processed.

### 7. Dashboard API
Single endpoint returns: total reservations, unassigned count, not-imported-to-PMS count, overbooking risk count, and average PMS sync delay — everything an operations supervisor needs at a glance.

### 8. Readable API Responses
Controllers return small response DTOs instead of exposing JPA entity graphs directly. Expected API errors return a consistent JSON body with `status`, `error`, `message`, and `path`.

---

## Domain Model

```
Room                        Reservation
────────────────            ─────────────────────────────────
id                          id
roomNumber                  channel (OtaChannel enum)
roomType (enum)             externalReservationId
capacity                    guestName
status (ACTIVE/INACTIVE)    checkInDate / checkOutDate
                            roomType / guestCount
                            paymentStatus (enum)
                            reservationStatus (enum)
RoomAssignment              pmsSyncStatus (enum)
─────────────────           emailReceivedAt
id                          pmsImportedAt
reservation  ──► FK
room         ──► FK         ReservationSyncLog
assignedCheckIn             ──────────────────
assignedCheckOut            id
assignmentStatus (enum)     channel / externalReservationId
                            eventType / message / createdAt
```

**Enums:** `OtaChannel` (BOOKING_COM, AGODA, TRIP_COM, AIRBNB) · `RoomType` (SINGLE, DOUBLE, TWIN, FAMILY) · `PmsSyncStatus` (PENDING_IMPORT → IMPORTED_TO_PMS → ACKNOWLEDGED) · `PaymentStatus` · `ReservationStatus` · `AssignmentStatus`

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/mock/booking/reservations/import` | Import mock reservations (idempotent) |
| `GET` | `/api/reservations` | List all reservations |
| `GET` | `/api/reservations/{id}` | Get single reservation |
| `GET` | `/api/reservations/{id}/room-candidates` | Available rooms for this reservation |
| `POST` | `/api/reservations/{id}/assign-room/{roomId}` | Assign room (400 for invalid room, 409 on duplicate assignment or date overlap) |
| `POST` | `/api/reservations/{id}/acknowledge` | Mark as acknowledged in PMS |
| `GET` | `/api/dashboard` | Operational dashboard metrics |

---

## Quick Start

```bash
mvn spring-boot:run
# Starts on port 8081
```

- Swagger UI → http://localhost:8081/swagger-ui.html
- H2 Console → http://localhost:8081/h2-console (URL: `jdbc:h2:mem:syncdb`, user: `sa`, password: empty)

**Note:** Rooms are seeded automatically on startup. Call the import endpoint first to load reservations.

---

## Curl Walkthrough

```bash
# 1. Import mock Booking.com reservations
curl -X POST http://localhost:8081/api/mock/booking/reservations/import
# → {"imported":3,"updated":0,"skipped":0,"message":"Import complete: 3 imported, 0 updated, 0 skipped."}

# 2. Import again — idempotency check
curl -X POST http://localhost:8081/api/mock/booking/reservations/import
# → {"imported":0,"updated":0,"skipped":3,"message":"Import complete: 0 imported, 0 updated, 3 skipped."}

# 3. List all reservations
curl http://localhost:8081/api/reservations

# 4. Get available rooms for reservation 1 (DOUBLE type, May 23–25)
curl http://localhost:8081/api/reservations/1/room-candidates
# → [{"id":1,"roomNumber":"201",...},{"id":2,"roomNumber":"202",...}]

# 5. Assign room 201 to reservation 1
curl -X POST http://localhost:8081/api/reservations/1/assign-room/1
# → {
#      "id": 1,
#      "reservationId": 1,
#      "roomId": 1,
#      "roomNumber": "201",
#      "roomType": "DOUBLE",
#      "assignedCheckIn": "2026-05-23",
#      "assignedCheckOut": "2026-05-25",
#      "assignmentStatus": "ASSIGNED"
#    }

# 6. Check candidates for reservation 2 (DOUBLE, May 24–26) — overlap detection
curl http://localhost:8081/api/reservations/2/room-candidates
# → Only room 202 returned. Room 201 (May 23–25) overlaps May 24–26 and is excluded.

# 7. Attempt to assign occupied room — conflict
curl -X POST http://localhost:8081/api/reservations/2/assign-room/1
# → HTTP 409 Conflict
# → {"status":409,"error":"409 CONFLICT","message":"Room 201 is already assigned for this date range.",...}

# 8. Acknowledge reservation 2
curl -X POST http://localhost:8081/api/reservations/2/acknowledge
# → {"pmsSyncStatus":"ACKNOWLEDGED",...}

# 9. Dashboard — operational summary
curl http://localhost:8081/api/dashboard
# → {
#      "totalReservationCount": 3,
#      "unassignedReservationCount": 2,
#      "notImportedToPmsCount": 0,
#      "overbookingRiskCount": 0,
#      "averagePmsSyncDelayMinutes": 3.5
#    }
```

---

## Interview Explanation

*One-minute spoken script:*

> "This project comes from something I observed working part-time at a hotel. Reservations come in from Booking.com, Agoda, Trip.com, and Airbnb — and I noticed that the Booking.com email notification arrives before the PMS updates. That delay is invisible unless you build something to track it.
>
> So I built a Spring Boot 3 backend that models the Booking.com reservation retrieve/acknowledge flow — but with a mock connector, no real API calls. The core ideas are: an interface-based connector so the OTA layer is swappable, idempotent import using channel plus external reservation ID as a composite key, and a PMS sync status lifecycle from PENDING_IMPORT through IMPORTED_TO_PMS to ACKNOWLEDGED.
>
> On top of that there's a room assignment service with date-overlap conflict detection, overbooking risk calculation, and a dashboard endpoint that surfaces all of this in one response.
>
> The demo runs on H2, but the JPA mapping is MySQL-friendly — standard column types, enum columns, and foreign keys. I would add a MySQL driver and production profile before deploying it.
>
> The part I find most interesting is the connector interface. If I ever got real Booking.com Connectivity Partner access, I'd just write a second implementation — none of the service logic changes."

---

## Test Coverage

```bash
mvn test
```

Minimal interview-focused tests cover:

- Reservation import does not create duplicate rows for the same `(channel, externalReservationId)`.
- Room recommendation excludes a room already assigned for overlapping stay dates.
- Assigning the same room to overlapping reservations fails with a conflict.
- Dashboard calculates pending PMS imports, unassigned reservations, and average PMS sync delay.

---

## Data and API Safety

- The project never calls the real Booking.com API.
- `MockBookingReservationConnector` uses hardcoded sample reservations only.
- Sample guest names are anonymous placeholders such as `Guest A`, `Guest B`, and `Guest C`.
- No real reservation numbers, customer data, payment details, hotel credentials, or API keys are included.

---

## Why This Is Relevant to the Job Description

| Job Requirement | This Project |
|---|---|
| Java / Spring | Java 17, Spring Boot 3, Spring Data JPA, Spring Web |
| RESTful API design | 7 REST endpoints with practical HTTP status codes (200, 400, 404, 409) |
| MySQL / RDBMS | JPA entities with MySQL-compatible mapping, `@UniqueConstraint`, enum columns |
| System monitoring | PMS sync delay tracking, unassigned reservation detection, overbooking risk |
| Dashboard development | `/api/dashboard` aggregates 5 operational metrics in one call |
| API integration | `BookingReservationConnector` interface models the OTA connectivity pattern |
| Hotel API experience | Based on real Booking.com retrieve/acknowledge flow; informed by on-the-job hotel observation |

---

## MVP Limitations

| Limitation | Honest explanation |
|---|---|
| No real Booking.com API call | Real access requires official Connectivity Partner authorization, credentials, retries, rate-limit handling, and production audit logging. |
| H2 only in this repo | The entity mapping is MySQL-friendly, but a MySQL driver/profile is not included in the MVP. |
| No frontend | The job-relevant backend dashboard data is exposed as JSON through `/api/dashboard`. |
| No authentication | Spring Security/JWT is intentionally left out to keep the interview project focused. |
| Limited concurrency hardening | The database unique constraint protects duplicates, but a production importer should also handle concurrent insert races with retry logic. |
| No reassignment workflow | The API prevents multiple active room assignments, but cancellation/reassignment endpoints are not implemented. |
| Basic error handling | Expected business errors return readable JSON, but full validation/i18n/error-code handling is out of scope. |

---

## Future Improvements

| Improvement | Notes |
|---|---|
| MySQL production profile | Add MySQL driver and `application-prod.properties` with DataSource settings |
| Real Booking.com connector | Implement an HTTP connector once partner access is granted |
| Channel connector routing | Add connector selection if Agoda, Trip.com, or Airbnb are implemented |
| PMS integration | Connect to a PMS through file export or official API if available |
| Sync delay alerting | Threshold-based alert, for example delay over 10 minutes |
| Spring Security | JWT-based API authentication for hotel staff roles |
