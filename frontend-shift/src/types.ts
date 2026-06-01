// Mirrors the shift-helper backend view records (adapter.in.web).

export interface Room {
  id: number;
  number: string;
  capacity: number;
  inService: boolean;
}

export interface Reservation {
  id: number;
  guestName: string;
  guestCount: number;
  checkIn: string; // ISO yyyy-MM-dd
  checkOut: string;
}

export type Change = 'NEW' | 'MOVED' | 'UNCHANGED' | 'LOCKED';

export interface Placement {
  reservationId: number;
  guestName: string;
  roomId: number;
  roomNumber: string;
  checkIn: string;
  checkOut: string;
  change: Change;
  previousRoomNumber: string | null;
}

export interface AssignmentPlan {
  feasible: boolean;
  targetReservationId: number;
  placements: Placement[];
  movedPlacements: Placement[];
  message: string;
}

export interface AssignmentView {
  id: number;
  reservationId: number;
  roomId: number;
  checkIn: string;
  checkOut: string;
}

// Client-only: a created reservation, flagged once committed.
export interface TrackedReservation extends Reservation {
  committed: boolean;
}
