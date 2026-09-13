import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AlarmFilters } from "../../components/alarms/AlarmFilters";
import { AlarmTable } from "../../components/alarms/AlarmTable";
import { ErrorBanner, PageHeader, Pagination, Spinner } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toastContext";
import type { AlarmResponse, PageResponse } from "../../types/api";

const PAGE_SIZE = 20;

export function AlarmsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [severity, setSeverity] = useState("");
  const [sourceType, setSourceType] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);

  const params = new URLSearchParams();
  if (status) params.set("status", status);
  if (severity) params.set("severity", severity);
  if (sourceType) params.set("sourceType", sourceType);
  params.set("page", String(page));
  params.set("size", String(PAGE_SIZE));

  const alarmsQuery = useQuery({
    queryKey: ["alarms", status, severity, sourceType, page],
    // 31단계: 페이지 응답
    queryFn: () => api.get<PageResponse<AlarmResponse>>(`/api/admin/alarms?${params.toString()}`),
    placeholderData: keepPreviousData,
    // 28단계: 알람 이벤트(SSE)가 오면 ["alarms"]가 무효화돼 바로 갱신된다. 폴링은 연결이 끊겼을 때를 위한 느린 안전장치만 남긴다.
    refetchInterval: 30000,
  });

  const { showToast } = useToast();
  const onError = (err: unknown) => setError(errorMessage(err));
  const onSuccess = (message: string) => () => {
    queryClient.invalidateQueries({ queryKey: ["alarms"] });
    setError(null);
    showToast(message);
  };

  const acknowledge = useMutation({
    mutationFn: (alarmId: number) => api.post<AlarmResponse>(`/api/admin/alarms/${alarmId}/acknowledge`),
    onSuccess: onSuccess("알람을 확인 처리했습니다."),
    onError,
  });

  const resolve = useMutation({
    mutationFn: (alarmId: number) => api.post<AlarmResponse>(`/api/admin/alarms/${alarmId}/resolve`),
    onSuccess: onSuccess("알람을 해제했습니다."),
    onError,
  });

  /** 필터를 바꾸면 첫 페이지로 돌아간다. */
  const resetPageAfter = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(0);
  };

  return (
    <div>
      <PageHeader title="알람" />
      <AlarmFilters
        status={status}
        severity={severity}
        sourceType={sourceType}
        onStatusChange={resetPageAfter(setStatus)}
        onSeverityChange={resetPageAfter(setSeverity)}
        onSourceTypeChange={resetPageAfter(setSourceType)}
      />
      <ErrorBanner message={error} />
      {alarmsQuery.isLoading && <Spinner />}
      {alarmsQuery.data && (
        <>
          <AlarmTable
            alarms={alarmsQuery.data.content}
            onAcknowledge={(id) => acknowledge.mutate(id)}
            onResolve={(id) => resolve.mutate(id)}
            isMutating={acknowledge.isPending || resolve.isPending}
          />
          <Pagination page={alarmsQuery.data} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
