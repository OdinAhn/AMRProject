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
          스케줄러가 없어 관리자가 필요할 때 직접 실행한다. 대기 중인 작업을 가능한 로봇에 배정하고, 배터리가 낮은 유휴
          로봇의 자동 충전 작업을 생성한다.
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
