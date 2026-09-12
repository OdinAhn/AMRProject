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
        {result.assignedTaskIds.length === 0
          ? "없음"
          : result.assignedTaskIds.map((id) => (
              <Link key={id} to={`/tasks/${id}`} className="mr-2 text-indigo-600 hover:underline">
                #{id}
              </Link>
            ))}
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
    </div>
  );
}
