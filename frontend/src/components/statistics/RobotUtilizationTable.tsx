import { Link } from "react-router";
import { formatDuration } from "../../lib/labels";
import { Card, Table, Td, Th } from "../ui";
import type { RobotUtilization } from "../../types/api";

/** 34단계: 로봇별 가동률(작업이 배정돼 있던 시간 비율)과 완료 작업 수. */
export function RobotUtilizationTable({ robots }: { robots: RobotUtilization[] }) {
  return (
    <Card>
      <h2 className="mb-1 text-sm font-semibold text-gray-700">로봇 가동률</h2>
      <p className="mb-3 text-xs text-gray-500">기간 중 이미 지난 시간 대비 작업이 배정돼 있던 시간입니다.</p>
      <Table>
        <thead>
          <tr>
            <Th>로봇</Th>
            <Th>가동률</Th>
            <Th>작업 시간</Th>
            <Th>완료 작업</Th>
          </tr>
        </thead>
        <tbody>
          {robots.map((robot) => (
            <tr key={robot.robotId}>
              <Td>
                <Link to={`/robots/${robot.robotId}`} className="font-medium text-indigo-600 hover:underline">
                  {robot.code}
                </Link>
                <span className="ml-2 text-xs text-gray-400">{robot.name}</span>
              </Td>
              <Td>
                <UtilizationBar pct={robot.utilizationPct} />
              </Td>
              <Td className="tabular-nums">{formatDuration(robot.busySeconds)}</Td>
              <Td className="tabular-nums">{robot.completedTasks}</Td>
            </tr>
          ))}
          {robots.length === 0 && (
            <tr>
              <Td className="text-gray-500">등록된 로봇이 없습니다.</Td>
            </tr>
          )}
        </tbody>
      </Table>
    </Card>
  );
}

function UtilizationBar({ pct }: { pct: number | null }) {
  if (pct === null) return <span className="text-gray-400">-</span>;
  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-24 overflow-hidden rounded-full bg-gray-200" role="meter" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className="h-full bg-indigo-500" style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs tabular-nums text-gray-600">{pct}%</span>
    </div>
  );
}
