import { useCallback, useEffect, useState } from "react";
import { api } from "./api";
import type { AssignmentPlan, Dashboard, Reservation, RoomCandidate } from "./types";

export default function App() {
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [flash, setFlash] = useState<{ text: string; kind: "ok" | "err" } | null>(null);
  const [assigningId, setAssigningId] = useState<number | null>(null);
  const [candidates, setCandidates] = useState<RoomCandidate[]>([]);
  const [autoAssignPlan, setAutoAssignPlan] = useState<AssignmentPlan | null>(null);
  const [autoAssignTargetId, setAutoAssignTargetId] = useState<number | null>(null);
  const [autoAssignLoading, setAutoAssignLoading] = useState(false);

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

  async function handleOpenAutoAssign(reservationId: number) {
    setAutoAssignTargetId(reservationId);
    setAutoAssignPlan(null);
    setAutoAssignLoading(true);
    try {
      const plan = await api.previewAutoAssign(reservationId);
      setAutoAssignPlan(plan);
    } catch (e) {
      showFlash((e as Error).message, "err");
      setAutoAssignTargetId(null);
    } finally {
      setAutoAssignLoading(false);
    }
  }

  async function handleCommitAutoAssign() {
    if (autoAssignTargetId == null) return;
    try {
      const plan = await api.commitAutoAssign(autoAssignTargetId);
      const moved = plan.movedPlacements.length;
      showFlash(
        moved > 0
          ? `자동 배정 완료. ${moved}건 재배치됨.`
          : "자동 배정 완료.",
        "ok"
      );
      setAutoAssignTargetId(null);
      setAutoAssignPlan(null);
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
                      <>
                        <button
                          onClick={() => handleOpenAutoAssign(r.id)}
                          className="px-3 py-1.5 text-xs bg-violet-100 hover:bg-violet-200 text-violet-800 rounded"
                          title="같은 룸타입 내에서 재배치를 통해 자동 배정"
                        >
                          자동 배정
                        </button>
                        <button
                          onClick={() => handleOpenAssign(r.id)}
                          className="px-3 py-1.5 text-xs bg-slate-100 hover:bg-slate-200 rounded"
                        >
                          수동 배정
                        </button>
                      </>
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

      {autoAssignTargetId != null && (
        <AutoAssignModal
          reservation={reservations.find((r) => r.id === autoAssignTargetId)!}
          plan={autoAssignPlan}
          loading={autoAssignLoading}
          onClose={() => {
            setAutoAssignTargetId(null);
            setAutoAssignPlan(null);
          }}
          onConfirm={handleCommitAutoAssign}
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

function AutoAssignModal({
  reservation,
  plan,
  loading,
  onClose,
  onConfirm,
}: {
  reservation: Reservation;
  plan: AssignmentPlan | null;
  loading: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) {
  // Group placements by room number for timeline-style display.
  const byRoom: Record<string, AssignmentPlan["placements"]> = {};
  if (plan) {
    for (const p of [...plan.placements].sort((a, b) =>
      a.checkInDate.localeCompare(b.checkInDate)
    )) {
      (byRoom[p.roomNumber] ??= []).push(p);
    }
  }
  const sortedRoomNumbers = Object.keys(byRoom).sort();

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-lg w-full max-w-3xl p-6 shadow-xl max-h-[90vh] overflow-y-auto">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h3 className="text-lg font-semibold">자동 배정 (재배치 미리보기)</h3>
            <p className="text-sm text-slate-500 mt-1">
              {reservation.externalReservationId} · {reservation.guestName} ·{" "}
              {reservation.checkInDate} → {reservation.checkOutDate} ·{" "}
              {reservation.roomType}
            </p>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700">
            ✕
          </button>
        </div>

        {loading && (
          <div className="text-sm text-slate-400 py-8 text-center">
            재배치 계획 계산 중...
          </div>
        )}

        {!loading && plan && !plan.feasible && (
          <div className="text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded p-4">
            <div className="font-medium mb-1">재배치 불가</div>
            <div>{plan.message}</div>
          </div>
        )}

        {!loading && plan && plan.feasible && (
          <>
            <div
              className={`text-sm rounded p-3 mb-4 border ${
                plan.movedPlacements.length > 0
                  ? "bg-amber-50 text-amber-800 border-amber-200"
                  : "bg-emerald-50 text-emerald-800 border-emerald-200"
              }`}
            >
              {plan.message}
            </div>

            <div className="space-y-3">
              {sortedRoomNumbers.map((roomNo) => (
                <div
                  key={roomNo}
                  className="border border-slate-200 rounded-md overflow-hidden"
                >
                  <div className="bg-slate-50 px-3 py-2 text-sm font-medium">
                    {roomNo}호
                  </div>
                  <ul className="divide-y divide-slate-100">
                    {byRoom[roomNo].map((p) => (
                      <li
                        key={`${p.reservationId}-${roomNo}`}
                        className="px-3 py-2 flex items-center justify-between text-sm"
                      >
                        <div className="flex items-center gap-2">
                          <ChangeBadge change={p.change} />
                          <span className="font-mono text-xs text-slate-500">
                            {p.externalReservationId}
                          </span>
                          <span>{p.guestName}</span>
                          <span className="text-slate-500 text-xs">
                            {p.checkInDate} → {p.checkOutDate}
                          </span>
                        </div>
                        {p.change === "MOVED" && p.previousRoomNumber && (
                          <span className="text-xs text-amber-700">
                            ← {p.previousRoomNumber}호에서 이동
                          </span>
                        )}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>

            <div className="flex justify-end gap-2 mt-6">
              <button
                onClick={onClose}
                className="px-4 py-2 text-sm bg-slate-100 hover:bg-slate-200 rounded"
              >
                취소
              </button>
              <button
                onClick={onConfirm}
                className="px-4 py-2 text-sm bg-violet-600 hover:bg-violet-700 text-white rounded"
              >
                {plan.movedPlacements.length > 0
                  ? `${plan.movedPlacements.length}건 재배치 후 배정`
                  : "배정 확정"}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function ChangeBadge({ change }: { change: AssignmentPlan["placements"][number]["change"] }) {
  const style: Record<typeof change, string> = {
    NEW: "bg-violet-100 text-violet-800 border-violet-200",
    MOVED: "bg-amber-100 text-amber-800 border-amber-200",
    UNCHANGED: "bg-slate-100 text-slate-600 border-slate-200",
    LOCKED: "bg-slate-200 text-slate-700 border-slate-300",
  };
  const label: Record<typeof change, string> = {
    NEW: "신규",
    MOVED: "이동",
    UNCHANGED: "유지",
    LOCKED: "고정",
  };
  return (
    <span className={`inline-flex px-2 py-0.5 text-xs rounded border ${style[change]}`}>
      {label[change]}
    </span>
  );
}
