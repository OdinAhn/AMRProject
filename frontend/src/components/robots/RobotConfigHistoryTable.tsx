import { Card, Table, Td, Th } from "../ui";
import type { RobotConfigurationResponse } from "../../types/api";

interface RobotConfigHistoryTableProps {
  configs: RobotConfigurationResponse[];
}

export function RobotConfigHistoryTable({ configs }: RobotConfigHistoryTableProps) {
  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">설정 이력</h2>
      <Table>
        <thead>
          <tr>
            <Th>버전</Th>
            <Th>최대 적재</Th>
            <Th>최대 속도</Th>
            <Th>배터리 용량</Th>
            <Th>저배터리 임계값</Th>
            <Th>충전 목표</Th>
          </tr>
        </thead>
        <tbody>
          {configs.map((config) => (
            <tr key={config.id}>
              <Td>v{config.versionNo}</Td>
              <Td>{config.maxPayloadKg} kg</Td>
              <Td>{config.maxSpeedMps} m/s</Td>
              <Td>{config.batteryCapacityWh} Wh</Td>
              <Td>{config.chargingLowThresholdPct}%</Td>
              <Td>{config.chargingTargetPct}%</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
