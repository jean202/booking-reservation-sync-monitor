import { useState, type FormEvent } from 'react';

interface Props {
  busy: boolean;
  onCreate: (r: {
    guestName: string;
    guestCount: number;
    checkIn: string;
    checkOut: string;
  }) => void;
}

function addDays(iso: string, days: number): string {
  const d = new Date(iso + 'T00:00:00');
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
}

const TODAY = new Date().toISOString().slice(0, 10);

export default function ReservationForm({ busy, onCreate }: Props) {
  const [guestName, setGuestName] = useState('');
  const [guestCount, setGuestCount] = useState('2');
  const [checkIn, setCheckIn] = useState(addDays(TODAY, 4));
  const [checkOut, setCheckOut] = useState(addDays(TODAY, 6));

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!guestName.trim()) return;
    onCreate({
      guestName: guestName.trim(),
      guestCount: Number(guestCount),
      checkIn,
      checkOut,
    });
    setGuestName('');
    setGuestCount('2');
  }

  return (
    <form onSubmit={submit} className="grid grid-cols-2 gap-3">
      <label className="col-span-2 block">
        <span className="field-label">Guest name</span>
        <input
          className="field-input"
          value={guestName}
          onChange={(e) => setGuestName(e.target.value)}
          placeholder="Reservation under…"
        />
      </label>
      <label className="block">
        <span className="field-label">Party size</span>
        <input
          className="field-input"
          type="number"
          min={1}
          value={guestCount}
          onChange={(e) => setGuestCount(e.target.value)}
        />
      </label>
      <div className="block" />
      <label className="block">
        <span className="field-label">Check-in</span>
        <input
          className="field-input"
          type="date"
          value={checkIn}
          onChange={(e) => {
            setCheckIn(e.target.value);
            if (e.target.value >= checkOut) setCheckOut(addDays(e.target.value, 1));
          }}
        />
      </label>
      <label className="block">
        <span className="field-label">Check-out</span>
        <input
          className="field-input"
          type="date"
          min={addDays(checkIn, 1)}
          value={checkOut}
          onChange={(e) => setCheckOut(e.target.value)}
        />
      </label>
      <button type="submit" className="btn-brass col-span-2 mt-1" disabled={busy}>
        Log reservation
      </button>
    </form>
  );
}
