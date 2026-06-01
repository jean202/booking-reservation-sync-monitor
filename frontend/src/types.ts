export type OtaChannel = "BOOKING_COM" | "AGODA" | "TRIP_COM" | "AIRBNB";
export type RoomType = "SINGLE" | "DOUBLE" | "TWIN" | "SUITE" | "FAMILY";
export type PaymentStatus = "PREPAID" | "PAY_AT_PROPERTY";
export type ReservationStatus = "CONFIRMED" | "CANCELLED";
export type PmsSyncStatus = "PENDING_IMPORT" | "IMPORTED_TO_PMS" | "ACKNOWLEDGED";

export interface Reservation {
  id: number;
  channel: OtaChannel;
  externalReservationId: string;
  guestName: string;
  checkInDate: string;
  checkOutDate: string;
  roomType: RoomType;
  guestCount: number;
  paymentStatus: PaymentStatus;
  reservationStatus: ReservationStatus;
  pmsSyncStatus: PmsSyncStatus;
  emailReceivedAt: string;
  pmsImportedAt: string | null;
  assignedRoomNumber: string | null;
}

export interface RoomCandidate {
  id: number;
  roomNumber: string;
  roomType: RoomType;
  capacity: number;
}

export interface RoomAssignment {
  id: number;
  reservationId: number;
  roomId: number;
  roomNumber: string;
  roomType: RoomType;
  assignedCheckIn: string;
  assignedCheckOut: string;
  assignmentStatus: string;
}

export interface Dashboard {
  totalReservationCount: number;
  unassignedReservationCount: number;
  notImportedToPmsCount: number;
  overbookingRiskCount: number;
  averagePmsSyncDelayMinutes: number;
}

export type PlacementChange = "NEW" | "MOVED" | "UNCHANGED" | "LOCKED";

export interface AssignmentPlanPlacement {
  reservationId: number;
  externalReservationId: string;
  guestName: string;
  roomId: number;
  roomNumber: string;
  checkInDate: string;
  checkOutDate: string;
  change: PlacementChange;
  previousRoomNumber: string | null;
}

export interface AssignmentPlan {
  feasible: boolean;
  targetReservationId: number;
  roomType: RoomType;
  placements: AssignmentPlanPlacement[];
  movedPlacements: AssignmentPlanPlacement[];
  message: string;
}

export interface ImportResult {
  imported: number;
  updated: number;
  skipped: number;
  message: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
