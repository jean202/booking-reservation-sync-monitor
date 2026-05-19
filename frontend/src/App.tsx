import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import type { Dashboard, Reservation, RoomCandidate } from "./types";

export default function App() {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [flash, setFlash] = useState<{ text: string; kind: "ok" | "err" } | null>(null);
  const [assigningId, setAssigningId] = useState<number | null>(null);
  const [candidates, setCandidates] = useState<RoomCandidate[]>([]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [d, r] = await Promise.all([api.getDashboard(), api.getReservations()]);
      setDashboard(d);
      setReservations(r);
    } catch (e) {
      showFlash((e as Error).message, "err");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  function showFlash(text: string, kind: "ok" | "err") {
    setFlash({ text, kind });
    setTimeout(() => setFlash(null), 3500);
  }

  async function handleImport() {
    try {
      const result = await api.importReservations();
      showFlash(result.message, "ok");
      await refresh();
    } catch (e) {
      showFlash((e as Error).message, "err");
    }
  }

  async function handleOpenAssign(reservationId: number) {
    setAssigningId(reservationId);
    setCandidates([]);
    try {
      const list = await api.getRoomCandidates(reservationId);
      setCandidates(list);
    } catch (e) {
      showFlash((e as Error).message, "err");
      setAssigningId(null);
    }
  }

  async function handleAssign(roomId: number) {
    if (assigningId == null) return;
    try {
      const result = await api.assignRoom(assigningId, roomId);
      showFlash(`${result.roomNumber}호 배정 완료`, "ok");
      setAssigningId(null);
      await refresh();
    } catch (e) {
      showFlash((e as Error).message, "err");
    }
  }

  async function handleAcknowledge(reservationId: number) {
    try {
      await api.acknowledge(reservationId);
      showFlash("PMS acknowledge 완료", "ok");
      await refresh();
    } catch (e) {
      showFlash((e as Error).message, "err");
    }
  }

  return (
    <div className="min-h-screen p-6 md:p-10 max-w-6xl mx-auto">
      <header className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Booking Reservation Sync Monitor</h1>
          <p className="text-sm text-slate-500 mt-1">
            OTA → PMS 동기화 상태를 모니터링하고 객실 배정/Ack를 처리합니다.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleImport}
            className="px-4 py-2 bg-indigo-600 text-white rounded-md hover:bg-indigo-700 text-sm"
          >
            예약 Import
          </button>
          <button
            onClick={refresh}
            className="px-4 py-2 bg-slate-200 rounded-md hover:bg-slate-300 text-sm"
          >
            새로고침
          </button>
        </div>
      </header>

      {flash && (
        <div
          className={`mb-4 px-4 py-2 rounded-md text-sm ${
            flash.kind === "ok"
              ? "bg-emerald-50 text-emerald-700 border border-emerald-200"
              : "bg-rose-50 text-rose-700 border border-rose-200"
          }`}
        >
          {flash.text}
        </div>
      )}

      <DashboardCards dashboard={dashboard} loading={loading} />

      <section className="mt-8">
        <h2 className="text-lg font-semibold mb-3">예약 목록</h2>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-slate-600">
              <tr>
                <th className="text-left px-4 py-3 font-medium">External ID</th>
                <th className="text-left px-4 py-3 font-medium">게스트</th>
                <th className="text-left px-4 py-3 font-medium">기간</th>
                <th className="text-left px-4 py-3 font-medium">타입/인원</th>
                <th className="text-left px-4 py-3 font-medium">PMS 상태</th>
                <th className="text-right px-4 py-3 font-medium">액션</th>
              </tr>
            </thead>
            <tbody>
              {reservations.map((r) => (
                <tr key={r.id} className="border-t border-slate-100">
                  <td className="px-4 py-3 font-mono text-xs">{r.externalReservationId}</td>
                  <td className="px-4 py-3">{r.guestName}</td>
                  <td className="px-4 py-3 text-slate-600">
                    {r.checkInDate} → {r.checkOutDate}
                  </td>
                  <td className="px-4 py-3 text-slate-600">
                    {r.roomType} / {r.guestCount}인
                  </td>
                  <td className="px-4 py-3">
                    <StatusBadge status={r.pmsSyncStatus} />
                  </td>
                  <td className="px-4 py-3 text-right space-x-2 whitespace-nowrap">
                    {r.assignedRoomNumber ? (
                      <span className="inline-flex items-center px-3 py-1.5 text-xs bg-emerald-50 text-emerald-700 border border-emerald-200 rounded">
                        ✓ {r.assignedRoomNumber}호
                      </span>
                    ) : (
                      <button
                        onClick={() => handleOpenAssign(r.id)}
                        className="px-3 py-1.5 text-xs bg-slate-100 hover:bg-slate-200 rounded"
                      >
                        객실 배정
                      </button>
                    )}
                    <button
                      onClick={() => handleAcknowledge(r.id)}
                      disabled={r.pmsSyncStatus !== "IMPORTED_TO_PMS"}
                      className="px-3 py-1.5 text-xs bg-indigo-50 text-indigo-700 hover:bg-indigo-100 rounded disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      Ack
                    </button>
                  </td>
                </tr>
              ))}
              {reservations.length === 0 && !loading && (
                <tr>
                  <td colSpan={6} className="px-4 py-10 text-center text-slate-400">
                    예약이 없습니다. "예약 Import" 버튼을 눌러주세요.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {assigningId != null && (
        <AssignModal
          reservation={reservations.find((r) => r.id === assigningId)!}
          candidates={candidates}
          onClose={() => setAssigningId(null)}
          onPick={handleAssign}
        />
      )}
    </div>
  );
}

function DashboardCards({
  dashboard,
  loading,
}: {
  dashboard: Dashboard | null;
  loading: boolean;
}) {
  if (loading && !dashboard) return <div className="text-slate-400">Loading...</div>;
  if (!dashboard) return null;

  const cards: { label: string; value: number; tone: "neutral" | "ok" | "warn" | "err" }[] = [
    { label: "전체 예약", value: dashboard.totalReservationCount, tone: "neutral" },
    {
      label: "미배정",
      value: dashboard.unassignedReservationCount,
      tone: dashboard.unassignedReservationCount > 0 ? "warn" : "ok",
    },
    {
      label: "PMS 미반영",
      value: dashboard.notImportedToPmsCount,
      tone: dashboard.notImportedToPmsCount > 0 ? "warn" : "ok",
    },
    {
      label: "오버부킹 위험",
      value: dashboard.overbookingRiskCount,
      tone: dashboard.overbookingRiskCount > 0 ? "err" : "ok",
    },
    {
      label: "평균 동기화 지연(분)",
      value: dashboard.averagePmsSyncDelayMinutes,
      tone: "neutral",
    },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
      {cards.map((c) => (
        <div key={c.label} className="bg-white border border-slate-200 rounded-lg p-4">
          <div className="text-xs text-slate-500">{c.label}</div>
          <div
            className={`mt-2 text-2xl font-semibold ${
              c.tone === "warn"
                ? "text-amber-600"
                : c.tone === "err"
                ? "text-rose-600"
                : c.tone === "ok"
                ? "text-emerald-600"
                : "text-slate-900"
            }`}
          >
            {c.value}
          </div>
        </div>
      ))}
    </div>
  );
}

function StatusBadge({ status }: { status: Reservation["pmsSyncStatus"] }) {
  const style: Record<Reservation["pmsSyncStatus"], string> = {
    PENDING_IMPORT: "bg-amber-50 text-amber-700 border-amber-200",
    IMPORTED_TO_PMS: "bg-sky-50 text-sky-700 border-sky-200",
    ACKNOWLEDGED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 text-xs rounded border ${style[status]}`}>
      {status}
    </span>
  );
}

function AssignModal({
  reservation,
  candidates,
  onClose,
  onPick,
}: {
  reservation: Reservation;
  candidates: RoomCandidate[];
  onClose: () => void;
  onPick: (roomId: number) => void;
}) {
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg w-full max-w-lg p-6 shadow-xl">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-semibold">객실 배정</h3>
            <p className="text-sm text-slate-500 mt-1">
              {reservation.externalReservationId} · {reservation.guestName} ·{" "}
              {reservation.checkInDate} → {reservation.checkOutDate}
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            ✕
          </button>
        </div>

        {candidates.length === 0 ? (
          <div className="text-sm text-slate-400 py-8 text-center">
            배정 가능한 객실이 없습니다. (타입/인원 미스매치이거나 모든 객실이
            겹치는 날짜에 이미 배정됨)
          </div>
        ) : (
          <ul className="divide-y divide-slate-100 border border-slate-200 rounded">
            {candidates.map((c) => (
              <li key={c.id} className="flex items-center justify-between px-4 py-3">
                <div>
                  <div className="font-medium">{c.roomNumber}호</div>
                  <div className="text-xs text-slate-500">
                    {c.roomType} · {c.capacity}인
                  </div>
                </div>
                <button
                  onClick={() => onPick(c.id)}
                  className="px-3 py-1.5 text-sm bg-indigo-600 text-white rounded hover:bg-indigo-700"
                >
                  배정
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
