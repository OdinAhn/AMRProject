import { Link } from "react-router";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { TaskResponse, TaskStatus } from "../../types/api";

interface TaskTableProps {
  tasks: TaskResponse[];
}

function statusTone(status: TaskStatus) {
  if (status === "SUCCEEDED") return "green" as const;
  if (status === "FAILED" || status === "RECOVERY_REQUIRED") return "red" as const;
  if (status === "CANCELED" || status === "CANCEL_REQUESTED") return "gray" as const;
  return "blue" as const;
}

export function TaskTable({ tasks }: TaskTableProps) {
  return (
    <Card>
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
          {tasks.map((task) => (
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
          {tasks.length === 0 && (
            <tr>
              <Td className="text-gray-500">조건에 맞는 작업이 없습니다.</Td>
            </tr>
          )}
        </tbody>
      </Table>
    </Card>
  );
}
