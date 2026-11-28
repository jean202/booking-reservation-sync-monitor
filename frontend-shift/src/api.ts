import type {
  AssignmentPlan,
  AssignmentView,
  Reservation,
  Room,
} from './types';

const BASE = '/api/shift';

/** Error carrying the backend message + HTTP status. */
export class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
    });
  } catch {
    throw new ApiError('Cannot reach the backend on :8082.', 0);
  }

  if (!res.ok) {
    let message = res.statusText || `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body && typeof body.message === 'string' && body.message) {
        message = body.message;
      }
    } catch {
      /* non-JSON error body; keep statusText */
    }
    throw new ApiError(message, res.status);
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  listRooms: () => request<Room[]>('/rooms'),

  registerRoom: (number: string, capacity: number) =>
    request<Room>('/rooms', {
      method: 'POST',
      body: JSON.stringify({ number, capacity }),
    }),

  toggleRoomService: (id: number, inService: boolean) =>
    request<Room>(`/rooms/${id}/service`, {
      method: 'PATCH',
      body: JSON.stringify({ inService }),
    }),

  createReservation: (r: {
    guestName: string;
    guestCount: number;
    checkIn: string;
    checkOut: string;
  }) =>
    request<Reservation>('/reservations', {
      method: 'POST',
      body: JSON.stringify(r),
    }),

  preview: (reservationId: number) =>
    request<AssignmentPlan>(
      `/reservations/${reservationId}/assignment/preview`,
      { method: 'POST' },
    ),

  commit: (reservationId: number) =>
    request<AssignmentPlan>(
      `/reservations/${reservationId}/assignment/commit`,
      { method: 'POST' },
    ),

  listAssignments: () => request<AssignmentView[]>('/assignments'),
};
