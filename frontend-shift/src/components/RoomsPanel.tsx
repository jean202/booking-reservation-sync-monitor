import { useState, type FormEvent } from 'react';
import type { Room } from '../types';

interface Props {
  rooms: Room[];
  busy: boolean;
  onRegister: (number: string, capacity: number) => void;
  onToggle: (id: number, inService: boolean) => void;
}

export default function RoomsPanel({ rooms, busy, onRegister, onToggle }: Props) {
  const [number, setNumber] = useState('');
  const [capacity, setCapacity] = useState('2');

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!number.trim()) return;
    onRegister(number.trim(), Number(capacity));
    setNumber('');
    setCapacity('2');
  }

  return (
    <section className="rounded-xl border border-ink-600 bg-ink-800/60 p-5 shadow-plate backdrop-blur-sm">
      <header className="mb-4 flex items-baseline justify-between">
        <h2 className="font-display text-xl text-cream-50">Rooms</h2>
        <span className="font-mono text-[0.6rem] uppercase tracking-[0.2em] text-cream-400">
          {rooms.length} on floor
        </span>
      </header>

      <form onSubmit={submit} className="grid grid-cols-[1fr_auto_auto] gap-3">
        <label className="block">
          <span className="field-label">Room no.</span>
          <input
            className="field-input"
            value={number}
            onChange={(e) => setNumber(e.target.value)}
            placeholder="101"
          />
        </label>
        <label className="block w-24">
          <span className="field-label">Capacity</span>
          <input
            className="field-input"
            type="number"
            min={1}
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
          />
        </label>
        <button type="submit" className="btn-brass mt-[1.35rem]" disabled={busy}>
          Add
        </button>
      </form>

      <ul className="mt-5 space-y-1.5">
        {rooms.length === 0 && (
          <li className="py-6 text-center font-sans text-sm text-cream-400/70">
            No rooms registered yet.
          </li>
        )}
        {rooms.map((r) => (
          <li
            key={r.id}
            className="flex items-center justify-between rounded-lg border border-ink-600/70 bg-ink-900/50 px-3.5 py-2.5"
          >
            <div className="flex items-center gap-3">
              <span className="font-mono text-base font-bold text-brass-300">
                {r.number}
              </span>
              <span className="font-mono text-[0.7rem] text-cream-400">
                · {r.capacity} pax
              </span>
            </div>
            <button
              onClick={() => onToggle(r.id, !r.inService)}
              disabled={busy}
              className="group flex items-center gap-2 font-mono text-[0.6rem] uppercase tracking-[0.16em] disabled:opacity-40"
            >
              <span
                className={`inline-block h-1.5 w-1.5 rounded-full ${
                  r.inService ? 'bg-sage animate-glow' : 'bg-rust'
                }`}
              />
              <span
                className={
                  r.inService
                    ? 'text-sage group-hover:text-cream-200'
                    : 'text-rust group-hover:text-cream-200'
                }
              >
                {r.inService ? 'in service' : 'out'}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
