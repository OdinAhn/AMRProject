import { Link } from "react-router";
import type { DispatchResultResponse } from "../../types/api";

interface DispatchResultSummaryProps {
  result: DispatchResultResponse;
}

export function DispatchResultSummary({ result }: DispatchResultSummaryProps) {
  return (
    <div className="mt-4 space-y-2 text-sm">
      <div>
        <span className="font-medium text-gray-700">배정된 작업:</span>{" "}
        {result.assignments.length === 0 && "없음"}
        {/* 38단계: 작업마다 고른 로봇과 출발 작업장까지 거리 — 가까운 로봇을 골랐는지 확인할 수 있다 */}
        {result.assignments.length > 0 && (
          <ul className="mt-1 space-y-1">
            {result.assignments.map((assignment) => (
              <li key={assignment.taskId}>
                <Link to={`/tasks/${assignment.taskId}`} className="text-indigo-600 hover:underline">
                  작업 #{assignment.taskId}
                </Link>
                {" → "}
                <Link to={`/robots/${assignment.robotId}`} className="text-indigo-600 hover:underline">
                  {assignment.robotCode}
                </Link>
                <span className="ml-2 text-xs text-gray-500">
                  {assignment.distanceToOrigin === null
                    ? "(위치·경로 정보 없음 — 등록 순서로 선택)"
                    : `(출발지까지 통로 거리 ${assignment.distanceToOrigin})`}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div>
        <span className="font-medium text-gray-700">생성된 충전 작업:</span>{" "}
        {result.chargeTaskIds.length === 0
          ? "없음"
          : result.chargeTaskIds.map((id) => (
              <Link key={id} to={`/tasks/${id}`} className="mr-2 text-indigo-600 hover:underline">
                #{id}
              </Link>
            ))}
      </div>
      <div>
        <span className="font-medium text-gray-700">대기 구역으로 보낸 로봇:</span>{" "}
        {result.parkedRobotIds.length === 0
          ? "없음"
          : result.parkedRobotIds.map((id) => (
              <Link key={id} to={`/robots/${id}`} className="mr-2 text-indigo-600 hover:underline">
                #{id}
              </Link>
            ))}
      </div>
    </div>
  );
}
