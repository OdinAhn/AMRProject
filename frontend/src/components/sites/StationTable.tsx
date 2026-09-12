import { Badge, Card, Table, Td, Th } from "../ui";
import type { StationResponse } from "../../types/api";
import { StationMapBindingForm, type BindableNode } from "./StationMapBindingForm";

interface StationTableProps {
  stations: StationResponse[];
  onUpdateCapacity: (stationId: number, maxConcurrentRobots: number | null) => void;
  bindableNodes: BindableNode[];
  onBindNode: (stationId: number, node: BindableNode) => void;
  isBindPending: boolean;
}

export function StationTable({ stations, onUpdateCapacity, bindableNodes, onBindNode, isBindPending }: StationTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>코드</Th>
            <Th>이름</Th>
            <Th>유형</Th>
            <Th>상태</Th>
            <Th>동시 최대 로봇 수</Th>
            <Th>지도 연결</Th>
          </tr>
        </thead>
        <tbody>
          {stations.map((station) => (
            <tr key={station.id}>
              <Td>{station.code}</Td>
              <Td>{station.name}</Td>
              <Td>
                <Badge tone="blue">{station.stationType}</Badge>
              </Td>
              <Td>{station.status}</Td>
              <Td>
                <input
                  type="number"
                  min={0}
                  defaultValue={station.maxConcurrentRobots ?? ""}
                  placeholder="무제한"
                  className="w-24 rounded-md border border-gray-300 px-2 py-1 text-sm"
                  onBlur={(e) => {
                    const value = e.target.value === "" ? null : Number(e.target.value);
                    if (value !== station.maxConcurrentRobots) {
                      onUpdateCapacity(station.id, value);
                    }
                  }}
                />
              </Td>
              <Td>
                <StationMapBindingForm
                  nodes={bindableNodes}
                  onBind={(node) => onBindNode(station.id, node)}
                  isPending={isBindPending}
                />
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
