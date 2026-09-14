import { Card } from "../ui";
import type { DailyTaskCount } from "../../types/api";

const CHART_HEIGHT_PX = 160;

/**
 * 34단계: 일별 접수·종료 작업 수. 날짜마다 접수(연한 막대)와 종료(성공·실패·취소를 쌓은 막대)를 나란히 그린다.
 * 차트 라이브러리 없이 CSS 막대로 충분하다 — 기간이 길면 가로로 스크롤된다.
 */
export function DailyThroughputChart({ daily }: { daily: DailyTaskCount[] }) {
  const max = Math.max(1, ...daily.map((d) => Math.max(d.created, d.succeeded + d.failed + d.canceled)));
  const labelEvery = daily.length <= 14 ? 1 : 7;
  const scale = (value: number) => `${(value / max) * CHART_HEIGHT_PX}px`;

  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-sm font-semibold text-gray-700">일별 작업 처리량</h2>
        <div className="flex flex-wrap gap-3 text-xs text-gray-500">
          <Legend className="bg-indigo-200" label="접수" />
          <Legend className="bg-green-500" label="성공" />
          <Legend className="bg-red-500" label="실패" />
          <Legend className="bg-gray-400" label="취소" />
        </div>
      </div>
      <div className="overflow-x-auto">
        <div className="flex min-w-full items-end gap-2" style={{ height: CHART_HEIGHT_PX + 24 }}>
          {daily.map((day, index) => (
            <div key={day.date} className="flex min-w-7 flex-1 flex-col items-center">
              <div
                className="flex items-end gap-0.5"
                style={{ height: CHART_HEIGHT_PX }}
                title={`${day.date} — 접수 ${day.created} · 성공 ${day.succeeded} · 실패 ${day.failed} · 취소 ${day.canceled}`}
              >
                <div className="w-2.5 rounded-t bg-indigo-200" style={{ height: scale(day.created) }} />
                <div className="flex w-2.5 flex-col-reverse overflow-hidden rounded-t">
                  <div className="bg-green-500" style={{ height: scale(day.succeeded) }} />
                  <div className="bg-red-500" style={{ height: scale(day.failed) }} />
                  <div className="bg-gray-400" style={{ height: scale(day.canceled) }} />
                </div>
              </div>
              <div className="mt-1 h-4 text-[10px] tabular-nums text-gray-500">
                {index % labelEvery === 0 ? day.date.slice(5) : ""}
              </div>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
}

function Legend({ className, label }: { className: string; label: string }) {
  return (
    <span className="flex items-center gap-1">
      <span className={`inline-block h-2.5 w-2.5 rounded-sm ${className}`} />
      {label}
    </span>
  );
}
