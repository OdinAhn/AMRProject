import { useState, type FormEvent } from "react";
import { Button, Card, Input, Select, Table, Td, Th } from "../ui";
import type { CreateZoneRequest, NodeResponse, ZoneResponse, ZoneType } from "../../types/api";

const ZONE_TYPES: ZoneType[] = ["MUTEX_ZONE", "SAFETY_ZONE", "PARKING"];

// 48단계: PARKING은 할 일 없는 로봇이 쉬는 대기 구역(예: 선반이 모여 있는 곳 아래) — 구역마다 고르게 채운다
const ZONE_TYPE_LABELS: Record<ZoneType, string> = {
  MUTEX_ZONE: "MUTEX_ZONE",
  SAFETY_ZONE: "SAFETY_ZONE",
  PARKING: "PARKING (대기 구역)",
};

interface ZonePanelProps {
  nodes: NodeResponse[];
  zones: ZoneResponse[];
  isDraft: boolean;
  onAddZone: (body: CreateZoneRequest) => void;
  isPending: boolean;
  resetToken: number;
}

export function ZonePanel({ nodes, zones, isDraft, onAddZone, isPending, resetToken }: ZonePanelProps) {
  const [zoneForm, setZoneForm] = useState({ code: "", zoneType: "MUTEX_ZONE" as ZoneType, nodeIds: [] as string[] });

  // 32단계: 부모가 resetToken을 올리면(저장 성공 등) 입력값을 비운다. effect에서 setState하면 렌더가 한 번 더 돌아서,
  // 렌더 중에 이전 값과 비교해 바로 조정한다(React 권장 패턴).
  const [appliedResetToken, setAppliedResetToken] = useState(resetToken);
  if (appliedResetToken !== resetToken) {
    setAppliedResetToken(resetToken);
    setZoneForm({ code: "", zoneType: "MUTEX_ZONE", nodeIds: [] });
  }

  const nodeName = (id: number) => nodes.find((n) => n.id === id)?.code ?? id;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onAddZone({ code: zoneForm.code, zoneType: zoneForm.zoneType, nodeIds: zoneForm.nodeIds.map(Number) });
  };

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">구역</h2>
      {isDraft && (
        <form className="mb-3 space-y-2" onSubmit={handleSubmit}>
          <Input placeholder="코드" value={zoneForm.code} onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value })} required />
          <Select value={zoneForm.zoneType} onChange={(e) => setZoneForm({ ...zoneForm, zoneType: e.target.value as ZoneType })}>
            {ZONE_TYPES.map((t) => (
              <option key={t} value={t}>
                {ZONE_TYPE_LABELS[t]}
              </option>
            ))}
          </Select>
          <select
            multiple
            className="h-24 w-full rounded-md border border-gray-300 px-2 py-1 text-sm"
            value={zoneForm.nodeIds}
            onChange={(e) => setZoneForm({ ...zoneForm, nodeIds: Array.from(e.target.selectedOptions, (o) => o.value) })}
          >
            {nodes.map((n) => (
              <option key={n.id} value={n.id}>
                {n.code}
              </option>
            ))}
          </select>
          <Button type="submit" disabled={isPending} className="w-full">
            구역 추가
          </Button>
        </form>
      )}
      <Table>
        <thead>
          <tr>
            <Th>코드</Th>
            <Th>유형</Th>
            <Th>노드</Th>
          </tr>
        </thead>
        <tbody>
          {zones.map((zone) => (
            <tr key={zone.id}>
              <Td>{zone.code}</Td>
              <Td>{zone.zoneType}</Td>
              <Td>{zone.nodeIds.map(nodeName).join(", ")}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
