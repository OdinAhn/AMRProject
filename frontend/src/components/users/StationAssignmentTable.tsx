import { Card, Table, Td, Th } from "../ui";
import type { StationAssignmentResponse } from "../../types/api";

interface StationAssignmentTableProps {
  assignments: StationAssignmentResponse[];
  onRemove: (stationId: number) => void;
}

export function StationAssignmentTable({ assignments, onRemove }: StationAssignmentTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>작업장 코드</Th>
            <Th>부여 시각</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {assignments.map((assignment) => (
            <tr key={assignment.id}>
              <Td>{assignment.stationCode}</Td>
              <Td>{new Date(assignment.createdAt).toLocaleString()}</Td>
              <Td>
                <button className="text-sm text-red-600 hover:underline" onClick={() => onRemove(assignment.stationId)}>
                  회수
                </button>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
