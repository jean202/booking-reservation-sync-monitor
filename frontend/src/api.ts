import type {
  ApiError,
  AssignmentPlan,
  Dashboard,
  ImportResult,
  Reservation,
  RoomAssignment,
  RoomCandidate,
} from "./types";

const BASE = "http://localhost:8081";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE}${path}`, init);
  if (!res.ok) {
    const err = (await res.json().catch(() => null)) as ApiError | null;
    throw new Error(err?.message ?? `HTTP ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  getDashboard: () => request<Dashboard>("/api/dashboard"),
  getReservations: () => request<Reservation[]>("/api/reservations"),
  getRoomCandidates: (reservationId: number) =>
    request<RoomCandidate[]>(`/api/reservations/${reservationId}/room-candidates`),
  assignRoom: (reservationId: number, roomId: number) =>
    request<RoomAssignment>(
      `/api/reservations/${reservationId}/assign-room/${roomId}`,
      { method: "POST" }
    ),
  previewAutoAssign: (reservationId: number) =>
    request<AssignmentPlan>(
      `/api/reservations/${reservationId}/auto-assign/preview`,
      { method: "POST" }
    ),
  commitAutoAssign: (reservationId: number) =>
    request<AssignmentPlan>(
      `/api/reservations/${reservationId}/auto-assign/commit`,
      { method: "POST" }
    ),
  acknowledge: (reservationId: number) =>
    request<Reservation>(`/api/reservations/${reservationId}/acknowledge`, {
      method: "POST",
    }),
  importReservations: () =>
    request<ImportResult>("/api/mock/booking/reservations/import", {
      method: "POST",
    }),
};
