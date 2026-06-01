import type { AssignmentPlan, Change, Placement } from '../types';

interface Props {
  plan: AssignmentPlan;
  guestName: string;
  busy: boolean;
  onCommit: () => void;
  onDismiss: () => void;
}

const CHANGE_STYLE: Record<Change, { label: string; cls: string }> = {
  NEW: { label: 'new', cls: 'text-sage border-sage/40 bg-sage/10' },
  MOVED: { label: 'moved', cls: 'text-brass-300 border-brass-400/50 bg-brass-500/10' },
  UNCHANGED: { label: 'stays', cls: 'text-cream-400 border-ink-500 bg-ink-900/40' },
  LOCKED: { label: 'locked', cls: 'text-rust border-rust/40 bg-rust/10' },
};

function PlacementRow({ p }: { p: Placement }) {
  const s = CHANGE_STYLE[p.change];
  return (
    <tr className="border-t border-ink-600/60">
      <td className="py-2.5 pr-3">
        <div className="font-display text-[0.98rem] text-cream-50">{p.guestName}</div>
        <div className="font-mono text-[0.6rem] text-cream-400">
          {p.checkIn} → {p.checkOut}
        </div>
      </td>
      <td className="py-2.5 pr-3">
        <div className="flex items-center gap-2 font-mono">
          {p.change === 'MOVED' && p.previousRoomNumber && (
            <span className="text-cream-400/70 line-through">{p.previousRoomNumber}</span>
          )}
          {p.change === 'MOVED' && (
            <span className="text-brass-400">→</span>
          )}
          <span className="text-lg font-bold text-brass-300">{p.roomNumber}</span>
        </div>
      </td>
      <td className="py-2.5 text-right">
        <span
          className={`inline-block rounded-full border px-2.5 py-0.5 font-mono text-[0.58rem] uppercase tracking-[0.16em] ${s.cls}`}
        >
          {s.label}
        </span>
      </td>
    </tr>
  );
}

export default function PlanPreview({
  plan,
  guestName,
  busy,
  onCommit,
  onDismiss,
}: Props) {
  const feasible = plan.feasible;

  return (
    <div className="animate-riseIn rounded-xl border border-brass-500/30 bg-ink-800/80 shadow-lamp backdrop-blur-sm">
      <header className="flex items-start justify-between border-b border-ink-600 px-5 py-4">
        <div>
          <p className="font-mono text-[0.6rem] uppercase tracking-[0.22em] text-brass-400">
            Assignment preview
          </p>
          <h3 className="mt-1 font-display text-xl text-cream-50">{guestName}</h3>
        </div>
        <button
          onClick={onDismiss}
          className="font-mono text-[0.65rem] uppercase tracking-[0.16em] text-cream-400 hover:text-cream-100"
        >
          ✕
        </button>
      </header>

      <div
        className={`flex items-center gap-2.5 px-5 py-3 font-sans text-sm ${
          feasible ? 'text-sage' : 'text-rust'
        }`}
      >
        <span
          className={`inline-block h-2 w-2 rounded-full ${
            feasible ? 'bg-sage animate-glow' : 'bg-rust'
          }`}
        />
        <span className="text-cream-100">{plan.message}</span>
      </div>

      {plan.placements.length > 0 && (
        <div className="px-5 pb-2">
          <table className="w-full text-left">
            <thead>
              <tr className="font-mono text-[0.55rem] uppercase tracking-[0.2em] text-cream-400/70">
                <th className="pb-1 font-normal">Guest</th>
                <th className="pb-1 font-normal">Room</th>
                <th className="pb-1 text-right font-normal">Change</th>
              </tr>
            </thead>
            <tbody>
              {plan.placements.map((p) => (
                <PlacementRow key={p.reservationId} p={p} />
              ))}
            </tbody>
          </table>
        </div>
      )}

      <footer className="flex items-center justify-between gap-3 border-t border-ink-600 px-5 py-4">
        <p className="font-mono text-[0.6rem] text-cream-400">
          {plan.movedPlacements.length > 0
            ? `${plan.movedPlacements.length} pre-arrival guest(s) reshuffled`
            : 'no reshuffle required'}
        </p>
        <div className="flex items-center gap-2">
          <button onClick={onDismiss} className="btn-ghost">
            Dismiss
          </button>
          <button onClick={onCommit} className="btn-brass" disabled={!feasible || busy}>
            Commit assignment
          </button>
        </div>
      </footer>
    </div>
  );
}
