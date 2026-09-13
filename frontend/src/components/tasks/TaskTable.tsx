import { Link } from "react-router";
import { labels, priorityLabel, shortTaskNo, taskStatusTone } from "../../lib/labels";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { TaskResponse } from "../../types/api";

interface TaskTableProps {
  tasks: TaskResponse[];
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
            <Th>우선순위</Th>
            <Th>출발지 → 목적지</Th>
            <Th>로봇</Th>
            <Th>생성 시각</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.id}>
              <Td className="font-mono text-xs">{shortTaskNo(task.taskNo)}</Td>
              <Td>{labels.taskType(task.taskType)}</Td>
              <Td>
                <Badge tone={taskStatusTone(task.status)}>{labels.taskStatus(task.status)}</Badge>
              </Td>
              <Td>
                {task.priority > 0 ? (
                  <Badge tone={task.priority >= 9 ? "red" : "yellow"}>{priorityLabel(task.priority)}</Badge>
                ) : (
                  <span className="text-gray-400">보통</span>
                )}
              </Td>
              <Td className="whitespace-nowrap">
                {task.originStationName} → {task.destinationStationName}
              </Td>
              <Td>{task.assignedRobotCode ?? <span className="text-gray-400">-</span>}</Td>
              <Td className="whitespace-nowrap">{new Date(task.createdAt).toLocaleString()}</Td>
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
