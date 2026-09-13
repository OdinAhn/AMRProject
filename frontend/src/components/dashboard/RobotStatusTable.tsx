import { Link } from "react-router";
import { labels, shortTaskNo } from "../../lib/labels";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { RobotSummary } from "../../types/api";

export function RobotStatusTable({ robots }: { robots: RobotSummary[] }) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">로봇 상태</h2>
      <Table>
        <thead>
          <tr>
            <Th>로봇</Th>
            <Th>연결</Th>
            <Th>동작</Th>
            <Th>배터리</Th>
            <Th>현재 작업</Th>
          </tr>
        </thead>
        <tbody>
          {robots.map((robot) => (
            <tr key={robot.robotId}>
              <Td>
                <Link to={`/robots/${robot.robotId}`} className="font-medium text-indigo-600 hover:underline">
                  {robot.code}
                </Link>
                {robot.safetyState && robot.safetyState !== "NORMAL" && (
                  <span className="ml-2">
                    <Badge tone="red">{labels.safetyState(robot.safetyState)}</Badge>
                  </span>
                )}
                {robot.lifecycleStatus !== "IN_SERVICE" && (
                  <span className="ml-2 text-xs text-gray-400">{labels.robotLifecycle(robot.lifecycleStatus)}</span>
                )}
              </Td>
              <Td>
                {robot.connectionState ? (
                  <Badge tone={robot.connectionState === "ONLINE" ? "green" : robot.connectionState === "STALE" ? "yellow" : "red"}>
                    {labels.connectionState(robot.connectionState)}
                  </Badge>
                ) : (
                  <span className="text-gray-400">-</span>
                )}
              </Td>
              <Td>{robot.motionState ? labels.motionState(robot.motionState) : "-"}</Td>
              <Td>
                <BatteryBar pct={robot.batteryPct} />
              </Td>
              <Td>
                {robot.currentTaskId && robot.currentTaskNo ? (
                  <Link to={`/tasks/${robot.currentTaskId}`} className="font-mono text-xs text-indigo-600 hover:underline">
                    {shortTaskNo(robot.currentTaskNo)}
                  </Link>
                ) : (
                  <span className="text-gray-400">-</span>
                )}
              </Td>
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

export function BatteryBar({ pct }: { pct: number | null }) {
  if (pct === null) return <span className="text-gray-400">-</span>;
  const color = pct <= 20 ? "bg-red-500" : pct <= 40 ? "bg-amber-500" : "bg-green-500";
  return (
    <div className="flex items-center gap-2">
      <div className="h-2 w-16 overflow-hidden rounded-full bg-gray-200" role="meter" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
        <div className={`h-full ${color}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs tabular-nums text-gray-600">{pct}%</span>
    </div>
  );
}
