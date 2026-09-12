import { Badge, Card, Table, Td, Th } from "../ui";
import type { TaskStepResponse } from "../../types/api";

interface TaskStepsTableProps {
  steps: TaskStepResponse[];
}

export function TaskStepsTable({ steps }: TaskStepsTableProps) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">단계</h2>
      <Table>
        <thead>
          <tr>
            <Th>#</Th>
            <Th>유형</Th>
            <Th>상태</Th>
          </tr>
        </thead>
        <tbody>
          {steps.map((step) => (
            <tr key={step.id}>
              <Td>{step.stepNo}</Td>
              <Td>{step.stepType}</Td>
              <Td>
                <Badge tone={step.status === "SUCCEEDED" ? "green" : step.status === "FAILED" ? "red" : "gray"}>{step.status}</Badge>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
