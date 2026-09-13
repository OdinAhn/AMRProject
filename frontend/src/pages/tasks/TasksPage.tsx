import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { TaskFilters } from "../../components/tasks/TaskFilters";
import { TaskTable } from "../../components/tasks/TaskTable";
import { PageHeader, Spinner } from "../../components/ui";
import { api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { TaskResponse } from "../../types/api";

export function TasksPage() {
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  // 30단계: 대시보드 타일에서 /tasks?status=... 로 들어오면 그 상태로 필터링된 채 시작한다.
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState(searchParams.get("status") ?? "");

  const params = new URLSearchParams();
  if (siteId) params.set("siteId", siteId);
  if (status) params.set("status", status);

  const tasksQuery = useQuery({
    queryKey: ["tasks", siteId, status],
    queryFn: () => api.get<TaskResponse[]>(`/api/admin/tasks?${params.toString()}`),
  });

  return (
    <div>
      <PageHeader title="작업" />

      <TaskFilters sites={sitesQuery.data ?? []} siteId={siteId} status={status} onSiteIdChange={setSiteId} onStatusChange={setStatus} />

      {tasksQuery.isLoading && <Spinner />}
      {tasksQuery.data && <TaskTable tasks={tasksQuery.data} />}
    </div>
  );
}
