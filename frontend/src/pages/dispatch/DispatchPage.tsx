import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { Button, Card, ErrorBanner, PageHeader, Select } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { DispatchResultResponse } from "../../types/api";

export function DispatchPage() {
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  const [error, setError] = useState<string | null>(null);

  const runDispatch = useMutation({
    mutationFn: () => api.post<DispatchResultResponse>(`/api/admin/sites/${siteId}/dispatch`),
    onError: (err) => setError(err instanceof ApiError ? err.message : "디스패치 실행에 실패했습니다."),
  });

  return (
    <div>
      <PageHeader title="디스패치 사이클" />
      <Card>
        <p className="mb-4 text-sm text-gray-500">
          스케줄러가 없어 관리자가 필요할 때 직접 실행한다. 대기 중인 작업을 가능한 로봇에 배정하고, 배터리가 낮은 유휴
          로봇의 자동 충전 작업을 생성한다.
        </p>
        <div className="flex items-end gap-3">
          <div className="w-56">
            <Select value={siteId} onChange={(e) => setSiteId(e.target.value)}>
              <option value="">물류센터 선택</option>
              {(sitesQuery.data ?? []).map((site) => (
                <option key={site.id} value={site.id}>
                  {site.name} ({site.code})
                </option>
              ))}
            </Select>
          </div>
          <Button onClick={() => runDispatch.mutate()} disabled={!siteId || runDispatch.isPending}>
            디스패치 실행
          </Button>
        </div>
        <ErrorBanner message={error} />

        {runDispatch.data && (
          <div className="mt-4 space-y-2 text-sm">
            <div>
              <span className="font-medium text-gray-700">배정된 작업:</span>{" "}
              {runDispatch.data.assignedTaskIds.length === 0
                ? "없음"
                : runDispatch.data.assignedTaskIds.map((id) => (
                    <Link key={id} to={`/tasks/${id}`} className="mr-2 text-indigo-600 hover:underline">
                      #{id}
                    </Link>
                  ))}
            </div>
            <div>
              <span className="font-medium text-gray-700">생성된 충전 작업:</span>{" "}
              {runDispatch.data.chargeTaskIds.length === 0
                ? "없음"
                : runDispatch.data.chargeTaskIds.map((id) => (
                    <Link key={id} to={`/tasks/${id}`} className="mr-2 text-indigo-600 hover:underline">
                      #{id}
                    </Link>
                  ))}
            </div>
          </div>
        )}
      </Card>
    </div>
  );
}
