import { Link } from "react-router";
import type { SiteOverviewResponse } from "../../types/api";

interface KpiTilesProps {
  overview: SiteOverviewResponse;
}

export function KpiTiles({ overview }: KpiTilesProps) {
  const counts = overview.openTaskCounts;
  const inService = overview.robots.filter((r) => r.lifecycleStatus === "IN_SERVICE");
  const online = inService.filter((r) => r.connectionState === "ONLINE").length;
  const inProgress = (counts.ASSIGNED ?? 0) + (counts.RUNNING ?? 0) + (counts.PAUSED ?? 0) + (counts.CANCEL_REQUESTED ?? 0);
  const recovery = counts.RECOVERY_REQUIRED ?? 0;

  return (
    <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-5">
      <Tile label="온라인 로봇" value={`${online} / ${inService.length}`} hint="운영 중 로봇 기준" tone={online < inService.length ? "warn" : "normal"} />
      <Tile label="진행 중 작업" value={inProgress} to="/tasks?status=RUNNING" />
      <Tile label="대기 작업" value={counts.QUEUED ?? 0} to="/tasks?status=QUEUED" />
      <Tile label="복구 필요" value={recovery} tone={recovery > 0 ? "alert" : "normal"} to="/tasks?status=RECOVERY_REQUIRED" />
      <Tile label="열린 알람" value={overview.openAlarmCount} tone={overview.openAlarmCount > 0 ? "alert" : "normal"} to="/alarms" />
    </div>
  );
}

function Tile({
  label,
  value,
  hint,
  tone = "normal",
  to,
}: {
  label: string;
  value: number | string;
  hint?: string;
  tone?: "normal" | "warn" | "alert";
  to?: string;
}) {
  const valueColor = tone === "alert" ? "text-red-600" : tone === "warn" ? "text-amber-600" : "text-gray-900";
  const body = (
    <div className="h-full rounded-lg border border-gray-200 bg-white p-4 shadow-sm hover:border-indigo-300">
      <div className="text-xs font-medium text-gray-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold tabular-nums ${valueColor}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-gray-400">{hint}</div>}
    </div>
  );
  return to ? <Link to={to}>{body}</Link> : body;
}
