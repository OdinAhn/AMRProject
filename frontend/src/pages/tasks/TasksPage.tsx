import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { TaskFilters } from "../../components/tasks/TaskFilters";
import { TaskTable } from "../../components/tasks/TaskTable";
import { PageHeader, Spinner } from "../../components/ui";
import { api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { TaskResponse } from "../../types/api";

export function TasksPage() {
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  const [status, setStatus] = useState("");

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
