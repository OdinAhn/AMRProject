import { useMutation } from "@tanstack/react-query";
import { useState } from "react";
import { DispatchForm } from "../../components/dispatch/DispatchForm";
import { DispatchResultSummary } from "../../components/dispatch/DispatchResultSummary";
import { Card, ErrorBanner, PageHeader } from "../../components/ui";
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
          대기 중인 작업을 우선순위 순으로, 출발 작업장까지 지도 통로 거리가 가장 가까운 로봇에 배정하고(위치를 모르는 로봇은 뒤로),
          배터리가 낮은 유휴 로봇의 자동 충전 작업을 생성합니다. 이 사이클은
          서버가 몇 초마다 자동으로 실행하므로, 여기서는 즉시 한 번 더 실행하고 결과를 확인할 때 사용합니다.
        </p>
        <DispatchForm
          sites={sitesQuery.data ?? []}
          siteId={siteId}
          onSiteIdChange={setSiteId}
          onRun={() => runDispatch.mutate()}
          isPending={runDispatch.isPending}
        />
        <ErrorBanner message={error} />

        {runDispatch.data && <DispatchResultSummary result={runDispatch.data} />}
      </Card>
    </div>
  );
}
