import { labels } from "../../lib/labels";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { TaskStepResponse } from "../../types/api";

interface TaskStepsTableProps {
  steps: TaskStepResponse[];
}

function stepTone(status: TaskStepResponse["status"]) {
  if (status === "SUCCEEDED") return "green" as const;
  if (status === "FAILED") return "red" as const;
  if (status === "RUNNING") return "blue" as const;
  return "gray" as const;
}

export function TaskStepsTable({ steps }: TaskStepsTableProps) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">단계</h2>
      <Table>
        <thead>
          <tr>
            <Th>#</Th>
            <Th>단계</Th>
            <Th>상태</Th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step) => (
            <tr key={step.id}>
              <Td>{step.stepNo}</Td>
              <Td>{labels.taskStepType(step.stepType)}</Td>
              <Td>
                <Badge tone={stepTone(step.status)}>{labels.taskStepStatus(step.status)}</Badge>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
