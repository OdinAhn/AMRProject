import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AlarmFilters } from "../../components/alarms/AlarmFilters";
import { AlarmTable } from "../../components/alarms/AlarmTable";
import { ErrorBanner, PageHeader, Spinner } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toast";
import type { AlarmResponse } from "../../types/api";

export function AlarmsPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState("");
  const [severity, setSeverity] = useState("");
  const [sourceType, setSourceType] = useState("");
  const [error, setError] = useState<string | null>(null);

  const params = new URLSearchParams();
  if (status) params.set("status", status);
  if (severity) params.set("severity", severity);
  if (sourceType) params.set("sourceType", sourceType);

  const alarmsQuery = useQuery({
    queryKey: ["alarms", status, severity, sourceType],
    queryFn: () => api.get<AlarmResponse[]>(`/api/admin/alarms?${params.toString()}`),
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

  return (
    <div>
      <PageHeader title="알람" />
      <AlarmFilters
        status={status}
        severity={severity}
        sourceType={sourceType}
        onStatusChange={setStatus}
        onSeverityChange={setSeverity}
        onSourceTypeChange={setSourceType}
      />
      <ErrorBanner message={error} />
      {alarmsQuery.isLoading && <Spinner />}
      {alarmsQuery.data && (
        <AlarmTable
          alarms={alarmsQuery.data}
          onAcknowledge={(id) => acknowledge.mutate(id)}
          onResolve={(id) => resolve.mutate(id)}
          isMutating={acknowledge.isPending || resolve.isPending}
        />
      )}
    </div>
  );
}
