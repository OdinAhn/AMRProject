import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { Badge, Card, PageHeader, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { TaskResponse, TaskStatus } from "../../types/api";

const STATUSES: TaskStatus[] = [
  "QUEUED",
  "ASSIGNED",
  "RUNNING",
  "PAUSED",
  "CANCEL_REQUESTED",
  "CANCELED",
  "RECOVERY_REQUIRED",
  "SUCCEEDED",
  "FAILED",
];

function statusTone(status: TaskStatus) {
  if (status === "SUCCEEDED") return "green" as const;
  if (status === "FAILED" || status === "RECOVERY_REQUIRED") return "red" as const;
  if (status === "CANCELED" || status === "CANCEL_REQUESTED") return "gray" as const;
  return "blue" as const;
}

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

      <Card className="mb-4">
        <div className="flex gap-4">
          <div className="w-56">
            <Select value={siteId} onChange={(e) => setSiteId(e.target.value)}>
              <option value="">전체 물류센터</option>
              {(sitesQuery.data ?? []).map((site) => (
                <option key={site.id} value={site.id}>
                  {site.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="w-56">
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">전체 상태</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </Select>
          </div>
        </div>
      </Card>

      <Card>
        {tasksQuery.isLoading && <Spinner />}
        {tasksQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>번호</Th>
                <Th>유형</Th>
                <Th>상태</Th>
                <Th>생성 시각</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {tasksQuery.data.map((task) => (
                <tr key={task.id}>
                  <Td className="font-mono text-xs">{task.taskNo.slice(0, 8)}</Td>
                  <Td>{task.taskType}</Td>
                  <Td>
                    <Badge tone={statusTone(task.status)}>{task.status}</Badge>
                  </Td>
                  <Td>{new Date(task.createdAt).toLocaleString()}</Td>
                  <Td>
                    <Link to={`/tasks/${task.id}`} className="text-indigo-600 hover:underline">
                      상세
                    </Link>
                  </Td>
                </tr>
              ))}
              {tasksQuery.data.length === 0 && (
                <tr>
                  <Td className="text-gray-500">조건에 맞는 작업이 없습니다.</Td>
                </tr>
              )}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}
