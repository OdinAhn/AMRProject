import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AlarmFilters } from "../../components/alarms/AlarmFilters";
import { AlarmTable } from "../../components/alarms/AlarmTable";
import { ErrorBanner, PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
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
    refetchInterval: 5000,
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ["alarms"] });
  const onError = (err: unknown) => setError(err instanceof ApiError ? err.message : "요청이 실패했습니다.");

  const acknowledge = useMutation({
    mutationFn: (alarmId: number) => api.post<AlarmResponse>(`/api/admin/alarms/${alarmId}/acknowledge`),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
    onError,
  });

  const resolve = useMutation({
    mutationFn: (alarmId: number) => api.post<AlarmResponse>(`/api/admin/alarms/${alarmId}/resolve`),
    onSuccess: () => {
      invalidate();
      setError(null);
    },
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
