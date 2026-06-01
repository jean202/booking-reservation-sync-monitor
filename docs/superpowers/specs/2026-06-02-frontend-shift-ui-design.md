# frontend-shift UI — Design

Date: 2026-06-02
Status: Approved

## Goal

Build the real `frontend-shift` UI on top of the existing React 19 + Vite +
Tailwind scaffold, exercising the full shift-helper workflow: register rooms,
create reservations, preview an assignment plan, and commit it.

## Architecture

Single-page React SPA. Vite dev server proxies `/api/*` to the backend on
`http://localhost:8082` (already configured in `vite.config.ts`), so no CORS
config is needed. State is centralized in `App`; child components are
presentational and communicate through props/callbacks.

### Key constraint: no `GET /reservations` endpoint

The backend exposes no list-reservations endpoint. Therefore reservations that
have been **created but not yet committed** live only in client state.
`GET /api/shift/assignments` returns only committed assignments. The UI keeps a
`reservations` array in `App` state; when a reservation is committed it is
flagged in that array and the assignments panel is refreshed.

## Backend API surface (consumed)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/shift/rooms` | `{number, capacity}` | RoomView |
| GET | `/api/shift/rooms` | — | RoomView[] |
| PATCH | `/api/shift/rooms/{id}/service` | `{inService}` | RoomView |
| POST | `/api/shift/reservations` | `{guestName, guestCount, checkIn, checkOut}` | ReservationView |
| POST | `/api/shift/reservations/{id}/assignment/preview` | — | AssignmentPlan |
| POST | `/api/shift/reservations/{id}/assignment/commit` | — | AssignmentPlan |
| GET | `/api/shift/assignments` | — | AssignmentView[] |

Error shape (400, IllegalArgumentException): `{timestamp, status, message}`.
Unknown reservation on preview/commit → 404.

## Files

```
src/
  api.ts          Typed fetch wrapper for the 7 endpoints; on non-2xx parses
                  {message} and throws Error(message) (falls back to statusText).
  types.ts        Room, Reservation, AssignmentPlan, Placement, Change enum,
                  AssignmentView — mirroring backend records.
  App.tsx         State orchestration: rooms, reservations, assignments,
                  currentPlan, loading, error. Loads rooms + assignments on mount.
  components/
    RoomsPanel.tsx       Register form (number, capacity) + room list with
                         in-service toggle (PATCH).
    ReservationForm.tsx  Create form (guestName, guestCount, checkIn, checkOut).
    ReservationList.tsx  Created reservations; each row a "Preview" button and a
                         "committed" badge once committed.
    PlanPreview.tsx      AssignmentPlan view: feasibility banner, placements
                         table with NEW/MOVED/UNCHANGED/LOCKED badges, message,
                         and a "Commit" button disabled when infeasible.
    AssignmentsPanel.tsx GET /assignments active list.
```

## Data flow

- **Mount:** `GET /rooms` and `GET /assignments` in parallel.
- **Register room / toggle service:** POST / PATCH → refresh rooms.
- **Create reservation:** POST → append to `reservations` state.
- **Preview:** reservation row button → `POST .../preview` → set `currentPlan`
  → show PlanPreview.
- **Commit:** PlanPreview button → `POST .../commit` → on success refresh
  assignments, flag the reservation committed, clear `currentPlan`.

## Layout

Header + responsive 2-column grid: left = Rooms panel, right = Reservations
(form + list). PlanPreview appears as a panel/modal on the right. Full-width
Assignments panel at the bottom. Visual polish via the frontend-design skill.

## Error handling

The `api.ts` wrapper throws `Error(message)` on non-2xx, parsing the backend
`{message}` field (fallback to `statusText`). `App` catches and shows a top
inline error banner. Example: capacity 0 → 400 "Capacity must be positive."

## Verification / testing

No frontend test runner (vitest) is installed; out of scope for this MVP (YAGNI).
Verification is done by running the Vite dev server and exercising the full flow
in a browser preview — register rooms → create reservation → preview → commit →
confirm in assignments — plus screenshots. Backend already runs on 8082.

## Out of scope

- Editing/deleting rooms or reservations.
- Listing/persisting uncommitted reservations server-side.
- Authentication, multi-user, date-range calendar views.
- Frontend unit/integration test harness.
