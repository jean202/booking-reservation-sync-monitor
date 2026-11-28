import type { AssignmentView, Room } from '../types';

interface Props {
  assignments: AssignmentView[];
  rooms: Room[];
}

export default function AssignmentsPanel({ assignments, rooms }: Props) {
  const roomNo = (id: number) => rooms.find((r) => r.id === id)?.number ?? `#${id}`;

  return (
    <section className="rounded-xl border border-ink-600 bg-ink-800/60 p-5 shadow-plate backdrop-blur-sm">
      <header className="mb-4 flex items-baseline justify-between">
        <h2 className="font-display text-xl text-cream-50">Committed assignments</h2>
        <span className="font-mono text-[0.6rem] uppercase tracking-[0.2em] text-cream-400">
          {assignments.length} on the board
        </span>
      </header>

      {assignments.length === 0 ? (
        <p className="py-6 text-center font-sans text-sm text-cream-400/70">
          Nothing committed yet — preview a reservation and commit it.
        </p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {assignments.map((a) => (
            <div
              key={a.id}
              className="flex items-center gap-3 rounded-lg border border-ink-600/70 bg-ink-900/50 px-3.5 py-3"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-md border border-brass-500/40 bg-brass-500/10">
                <span className="font-mono text-sm font-bold text-brass-300">
                  {roomNo(a.roomId)}
                </span>
              </div>
              <div className="min-w-0 font-mono text-[0.65rem] text-cream-400">
                <div className="text-cream-200">res #{a.reservationId}</div>
                <div>
                  {a.checkIn} → {a.checkOut}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
