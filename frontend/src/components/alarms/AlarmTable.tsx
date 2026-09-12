import { Card, Table, Td, Th } from "../ui";
import type { AlarmResponse } from "../../types/api";
import { AlarmRow } from "./AlarmRow";

interface AlarmTableProps {
  alarms: AlarmResponse[];
  onAcknowledge: (id: number) => void;
  onResolve: (id: number) => void;
  isMutating: boolean;
}

export function AlarmTable({ alarms, onAcknowledge, onResolve, isMutating }: AlarmTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>종류</Th>
            <Th>심각도</Th>
            <Th>대상</Th>
            <Th>상태</Th>
            <Th>메시지</Th>
            <Th>발생 시각</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {alarms.map((alarm) => (
            <AlarmRow key={alarm.id} alarm={alarm} onAcknowledge={onAcknowledge} onResolve={onResolve} isMutating={isMutating} />
          ))}
          {alarms.length === 0 && (
            <tr>
              <Td className="text-gray-500">조건에 맞는 알람이 없습니다.</Td>
            </tr>
          )}
        </tbody>
      </Table>
    </Card>
  );
}
