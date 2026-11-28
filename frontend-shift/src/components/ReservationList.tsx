import type { TrackedReservation } from '../types';

interface Props {
  reservations: TrackedReservation[];
  activeId: number | null;
  busy: boolean;
  onPreview: (id: number) => void;
}

function nights(checkIn: string, checkOut: string): number {
  const a = new Date(checkIn + 'T00:00:00').getTime();
  const b = new Date(checkOut + 'T00:00:00').getTime();
  return Math.max(1, Math.round((b - a) / 86_400_000));
}

export default function ReservationList({
  reservations,
  activeId,
  busy,
  onPreview,
}: Props) {
  if (reservations.length === 0) {
    return (
      <p className="py-6 text-center font-sans text-sm text-cream-400/70">
        No reservations logged this shift.
      </p>
    );
  }

  return (
    <ul className="space-y-2">
      {reservations.map((r) => (
        <li
          key={r.id}
          className={`flex items-center justify-between rounded-lg border px-3.5 py-2.5 transition-colors ${
            activeId === r.id
              ? 'border-brass-400/70 bg-ink-700/70'
              : 'border-ink-600/70 bg-ink-900/50'
          }`}
        >
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className="truncate font-display text-[1.05rem] text-cream-50">
                {r.guestName}
              </span>
              <span className="font-mono text-[0.65rem] text-cream-400">
                ·&nbsp;{r.guestCount} pax
              </span>
            </div>
            <div className="mt-0.5 font-mono text-[0.65rem] tracking-wide text-cream-400">
              {r.checkIn} → {r.checkOut}
              <span className="text-cream-400/60">
                {' '}
                · {nights(r.checkIn, r.checkOut)}n
              </span>
            </div>
          </div>

          {r.committed ? (
            <span className="flex items-center gap-1.5 font-mono text-[0.6rem] uppercase tracking-[0.16em] text-sage">
              <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                <path
                  d="M2.5 6.2 5 8.5l4.5-5"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              assigned
            </span>
          ) : (
            <button
              onClick={() => onPreview(r.id)}
              disabled={busy}
              className="btn-ghost"
            >
              Preview
            </button>
          )}
        </li>
      ))}
    </ul>
  );
}
