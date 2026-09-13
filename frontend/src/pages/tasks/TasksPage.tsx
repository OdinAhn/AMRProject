import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { TaskFilters } from "../../components/tasks/TaskFilters";
import { TaskTable } from "../../components/tasks/TaskTable";
import { PageHeader, Pagination, Spinner } from "../../components/ui";
import { api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { PageResponse, TaskResponse } from "../../types/api";

const PAGE_SIZE = 20;

export function TasksPage() {
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState("");
  // 30단계: 대시보드 타일에서 /tasks?status=... 로 들어오면 그 상태로 필터링된 채 시작한다.
  const [searchParams] = useSearchParams();
  const [status, setStatus] = useState(searchParams.get("status") ?? "");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams();
  if (siteId) params.set("siteId", siteId);
  if (status) params.set("status", status);
  params.set("page", String(page));
  params.set("size", String(PAGE_SIZE));

  // 31단계: 목록이 페이지 응답으로 바뀌었다. 페이지를 넘길 때 이전 결과를 유지해 표가 깜빡이지 않게 한다.
  const tasksQuery = useQuery({
    queryKey: ["tasks", siteId, status, page],
    queryFn: () => api.get<PageResponse<TaskResponse>>(`/api/admin/tasks?${params.toString()}`),
    placeholderData: keepPreviousData,
  });

  const changeFilter = (setter: (value: string) => void) => (value: string) => {
    setter(value);
    setPage(0);
  };

  return (
    <div>
      <PageHeader title="작업" />

      <TaskFilters
        sites={sitesQuery.data ?? []}
        siteId={siteId}
        status={status}
        onSiteIdChange={changeFilter(setSiteId)}
        onStatusChange={changeFilter(setStatus)}
      />

      {tasksQuery.isLoading && <Spinner />}
      {tasksQuery.data && (
        <>
          <TaskTable tasks={tasksQuery.data.content} />
          <Pagination page={tasksQuery.data} onPageChange={setPage} />
        </>
      )}
    </div>
  );
}
