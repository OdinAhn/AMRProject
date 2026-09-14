import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { severityTone } from "../../components/alarms/alarmTone";
import { DailyThroughputChart } from "../../components/statistics/DailyThroughputChart";
import { RobotUtilizationTable } from "../../components/statistics/RobotUtilizationTable";
import { Badge, Button, Card, ErrorBanner, Input, PageHeader, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { formatDuration, labels } from "../../lib/labels";
import { useSites } from "../../lib/useSites";
import type { SiteStatisticsResponse } from "../../types/api";

const SITE_STORAGE_KEY = "amr.statistics.siteId";
const PRESETS = [
  { label: "오늘", days: 1 },
  { label: "최근 7일", days: 7 },
  { label: "최근 30일", days: 30 },
];

function readStoredSiteId(): string {
  try {
    return localStorage.getItem(SITE_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

/** 브라우저 시간대 기준 오늘에서 daysAgo일 전 날짜(YYYY-MM-DD). */
function localDate(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * 34단계: 물류센터 운영 통계. 기간(브라우저 시간대의 날짜)을 골라 처리량·성공률·평균 처리/대기 시간, 일별 추이,
 * 로봇 가동률, 발생 알람을 본다. 실시간 이벤트마다 다시 집계하면 부담이라 자동 갱신하지 않고 새로고침 버튼을 둔다.
 */
export function StatisticsPage() {
  const sitesQuery = useSites();
  const [selectedSiteId, setSelectedSiteId] = useState(readStoredSiteId);
  const sites = sitesQuery.data ?? [];
  const siteId = sites.some((s) => String(s.id) === selectedSiteId) ? selectedSiteId : sites[0] ? String(sites[0].id) : "";

  const [from, setFrom] = useState(() => localDate(6));
  const [to, setTo] = useState(() => localDate(0));
  const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;

  const statisticsQuery = useQuery({
    queryKey: ["statistics", siteId, from, to, zone],
    queryFn: () =>
      api.get<SiteStatisticsResponse>(
        `/api/admin/sites/${siteId}/statistics?${new URLSearchParams({ from, to, zone }).toString()}`,
      ),
    enabled: !!siteId && !!from && !!to,
  });

  const changeSite = (value: string) => {
    setSelectedSiteId(value);
    try {
      localStorage.setItem(SITE_STORAGE_KEY, value);
    } catch {
      // 저장소를 쓸 수 없는 환경이면 선택만 유지한다
    }
  };

  const applyPreset = (days: number) => {
    setFrom(localDate(days - 1));
    setTo(localDate(0));
  };

  const statistics = statisticsQuery.data;

  return (
    <div>
      <PageHeader
        title="운영 통계"
        actions={
          sites.length > 0 && (
            <div className="w-56">
              <Select value={siteId} onChange={(e) => changeSite(e.target.value)} aria-label="물류센터 선택">
                {sites.map((site) => (
                  <option key={site.id} value={site.id}>
                    {site.name} ({site.code})
                  </option>
                ))}
              </Select>
            </div>
          )
        }
      />

      {sitesQuery.data && sites.length === 0 && (
        <Card>
          <p className="text-sm text-gray-600">
            등록된 물류센터가 없습니다.{" "}
            <Link to="/sites" className="text-indigo-600 hover:underline">
              물류센터를 먼저 등록하세요.
            </Link>
          </p>
        </Card>
      )}

      {siteId && (
        <Card className="mb-4">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex gap-2">
              {PRESETS.map((preset) => (
                <Button
                  key={preset.days}
                  variant={from === localDate(preset.days - 1) && to === localDate(0) ? "primary" : "secondary"}
                  onClick={() => applyPreset(preset.days)}
                >
                  {preset.label}
                </Button>
              ))}
            </div>
            <div className="flex items-center gap-2 text-sm text-gray-600">
              <Input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} aria-label="시작일" className="w-40" />
              <span>~</span>
              <Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} aria-label="종료일" className="w-40" />
            </div>
            <Button variant="secondary" onClick={() => statisticsQuery.refetch()} disabled={statisticsQuery.isFetching}>
              새로고침
            </Button>
            <span className="text-xs text-gray-400">시간대: {zone} · 최대 92일</span>
          </div>
        </Card>
      )}

      <ErrorBanner message={statisticsQuery.error ? errorMessage(statisticsQuery.error) : null} />
      {statisticsQuery.isLoading && <Spinner />}

      {statistics && (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
            <Tile label="접수 작업" value={statistics.tasks.created} />
            <Tile label="성공" value={statistics.tasks.succeeded} />
            <Tile label="실패" value={statistics.tasks.failed} tone={statistics.tasks.failed > 0 ? "alert" : "normal"} />
            <Tile label="취소" value={statistics.tasks.canceled} />
            <Tile
              label="성공률"
              value={statistics.tasks.successRatePct === null ? "-" : `${statistics.tasks.successRatePct}%`}
              hint="성공 ÷ (성공 + 실패)"
            />
            <Tile
              label="평균 처리 시간"
              value={statistics.tasks.avgLeadTimeSeconds === null ? "-" : formatDuration(statistics.tasks.avgLeadTimeSeconds)}
              hint="성공 작업의 접수→완료"
            />
            <Tile
              label="평균 대기 시간"
              value={statistics.tasks.avgWaitSeconds === null ? "-" : formatDuration(statistics.tasks.avgWaitSeconds)}
              hint="접수→로봇 배정"
            />
          </div>

          <div className="mb-4">
            <DailyThroughputChart daily={statistics.daily} />
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
            <div className="xl:col-span-3">
              <RobotUtilizationTable robots={statistics.robots} />
            </div>
            <div className="xl:col-span-2">
              <Card>
                <h2 className="mb-3 text-sm font-semibold text-gray-700">발생 알람</h2>
                <Table>
                  <thead>
                    <tr>
                      <Th>종류</Th>
                      <Th>심각도</Th>
                      <Th>건수</Th>
                    </tr>
                  </thead>
                  <tbody>
                    {statistics.alarms.map((alarm) => (
                      <tr key={`${alarm.alarmType}-${alarm.severity}`}>
                        <Td>{labels.alarmType(alarm.alarmType)}</Td>
                        <Td>
                          <Badge tone={severityTone(alarm.severity)}>{labels.alarmSeverity(alarm.severity)}</Badge>
                        </Td>
                        <Td className="tabular-nums">{alarm.count}</Td>
                      </tr>
                    ))}
                    {statistics.alarms.length === 0 && (
                      <tr>
                        <Td className="text-gray-500">기간 안에 발생한 알람이 없습니다.</Td>
                      </tr>
                    )}
                  </tbody>
                </Table>
              </Card>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

function Tile({ label, value, hint, tone = "normal" }: { label: string; value: number | string; hint?: string; tone?: "normal" | "alert" }) {
  return (
    <div className="h-full rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="text-xs font-medium text-gray-500">{label}</div>
      <div className={`mt-1 text-2xl font-semibold tabular-nums ${tone === "alert" ? "text-red-600" : "text-gray-900"}`}>{value}</div>
      {hint && <div className="mt-1 text-xs text-gray-400">{hint}</div>}
    </div>
  );
}
