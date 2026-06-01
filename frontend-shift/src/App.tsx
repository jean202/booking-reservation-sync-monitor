import { useEffect, useState, useCallback } from 'react';
import { api, ApiError } from './api';
import type {
  AssignmentPlan,
  AssignmentView,
  Room,
  TrackedReservation,
} from './types';
import RoomsPanel from './components/RoomsPanel';
import ReservationForm from './components/ReservationForm';
import ReservationList from './components/ReservationList';
import PlanPreview from './components/PlanPreview';
import AssignmentsPanel from './components/AssignmentsPanel';

export default function App() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [reservations, setReservations] = useState<TrackedReservation[]>([]);
  const [assignments, setAssignments] = useState<AssignmentView[]>([]);
  const [plan, setPlan] = useState<AssignmentPlan | null>(null);
  const [activeId, setActiveId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fail = (e: unknown) =>
    setError(e instanceof ApiError ? e.message : 'Unexpected error.');

  const refreshRooms = useCallback(async () => {
    setRooms(await api.listRooms());
  }, []);
  const refreshAssignments = useCallback(async () => {
    setAssignments(await api.listAssignments());
  }, []);

  useEffect(() => {
    (async () => {
      try {
        await Promise.all([refreshRooms(), refreshAssignments()]);
      } catch (e) {
        fail(e);
      }
    })();
  }, [refreshRooms, refreshAssignments]);

  // Run an async action with shared busy/error handling.
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await fn();
    } catch (e) {
      fail(e);
    } finally {
      setBusy(false);
    }
  }

  const registerRoom = (number: string, capacity: number) =>
    run(async () => {
      await api.registerRoom(number, capacity);
      await refreshRooms();
    });

  const toggleRoom = (id: number, inService: boolean) =>
    run(async () => {
      await api.toggleRoomService(id, inService);
      await refreshRooms();
    });

  const createReservation = (r: {
    guestName: string;
    guestCount: number;
    checkIn: string;
    checkOut: string;
  }) =>
    run(async () => {
      const created = await api.createReservation(r);
      setReservations((prev) => [{ ...created, committed: false }, ...prev]);
    });

  const previewReservation = (id: number) =>
    run(async () => {
      const result = await api.preview(id);
      setPlan(result);
      setActiveId(id);
    });

  const commit = () =>
    run(async () => {
      if (activeId == null) return;
      await api.commit(activeId);
      await refreshAssignments();
      setReservations((prev) =>
        prev.map((r) => (r.id === activeId ? { ...r, committed: true } : r)),
      );
      setPlan(null);
      setActiveId(null);
    });

  const dismissPlan = () => {
    setPlan(null);
    setActiveId(null);
  };

  const activeGuest =
    reservations.find((r) => r.id === activeId)?.guestName ?? '';

  return (
    <div className="relative z-[2] mx-auto min-h-screen max-w-6xl px-5 py-10 sm:px-8">
      {/* Masthead */}
      <header className="mb-9 flex flex-col gap-2 border-b border-ink-600 pb-6 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="font-mono text-[0.62rem] uppercase tracking-[0.34em] text-brass-400">
            Front desk · night audit
          </p>
          <h1 className="mt-2 font-display text-4xl font-light italic text-cream-50 sm:text-5xl">
            Night&nbsp;Auditor
          </h1>
        </div>
        <p className="max-w-xs font-sans text-sm leading-relaxed text-cream-400">
          Hold the floor while the house sleeps — register rooms, log walk-ins,
          and let the planner reshuffle pre-arrivals.
        </p>
      </header>

      {error && (
        <div className="animate-riseIn mb-6 flex items-start justify-between gap-4 rounded-lg border border-rust/50 bg-rust/10 px-4 py-3">
          <p className="font-sans text-sm text-cream-100">
            <span className="font-mono text-[0.65rem] uppercase tracking-[0.18em] text-rust">
              error&nbsp;·&nbsp;
            </span>
            {error}
          </p>
          <button
            onClick={() => setError(null)}
            className="font-mono text-[0.7rem] text-cream-400 hover:text-cream-100"
          >
            ✕
          </button>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        <RoomsPanel
          rooms={rooms}
          busy={busy}
          onRegister={registerRoom}
          onToggle={toggleRoom}
        />

        <div className="flex flex-col gap-6">
          <section className="rounded-xl border border-ink-600 bg-ink-800/60 p-5 shadow-plate backdrop-blur-sm">
            <header className="mb-4">
              <h2 className="font-display text-xl text-cream-50">Reservations</h2>
            </header>
            <ReservationForm busy={busy} onCreate={createReservation} />
            <div className="mt-5 border-t border-ink-600/70 pt-4">
              <ReservationList
                reservations={reservations}
                activeId={activeId}
                busy={busy}
                onPreview={previewReservation}
              />
            </div>
          </section>

          {plan && (
            <PlanPreview
              plan={plan}
              guestName={activeGuest}
              busy={busy}
              onCommit={commit}
              onDismiss={dismissPlan}
            />
          )}
        </div>
      </div>

      <div className="mt-6">
        <AssignmentsPanel assignments={assignments} rooms={rooms} />
      </div>

      <footer className="mt-10 border-t border-ink-600 pt-5 text-center font-mono text-[0.58rem] uppercase tracking-[0.22em] text-cream-400/50">
        shift-helper · localhost:8082
      </footer>
    </div>
  );
}
