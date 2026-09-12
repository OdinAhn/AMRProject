import { useState } from "react";
import { Button, Select } from "../ui";
import type { NodeResponse } from "../../types/api";

export interface BindableNode extends NodeResponse {
  mapName: string;
}

interface StationMapBindingFormProps {
  nodes: BindableNode[];
  onBind: (node: BindableNode) => void;
  isPending: boolean;
}

export function StationMapBindingForm({ nodes, onBind, isPending }: StationMapBindingFormProps) {
  const [nodeId, setNodeId] = useState("");

  if (nodes.length === 0) {
    return <span className="text-xs text-gray-400">연결 가능한 활성 지도 노드 없음</span>;
  }

  return (
    <div className="flex items-center gap-2">
      <Select className="w-40" value={nodeId} onChange={(e) => setNodeId(e.target.value)}>
        <option value="">노드 선택</option>
        {nodes.map((node) => (
          <option key={node.id} value={node.id}>
            {node.code} ({node.mapName})
          </option>
        ))}
      </Select>
      <Button
        variant="secondary"
        disabled={!nodeId || isPending}
        onClick={() => {
          const node = nodes.find((n) => n.id === Number(nodeId));
          if (node) onBind(node);
        }}
      >
        연결
      </Button>
    </div>
  );
}
