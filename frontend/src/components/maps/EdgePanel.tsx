import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, Input, Select, Table, Td, Th } from "../ui";
import type { CreateEdgeRequest, EdgeDirection, EdgeResponse, NodeResponse } from "../../types/api";

const EDGE_DIRECTIONS: EdgeDirection[] = ["ONE_WAY", "TWO_WAY"];

interface EdgePanelProps {
  nodes: NodeResponse[];
  edges: EdgeResponse[];
  isDraft: boolean;
  onAddEdge: (body: CreateEdgeRequest) => void;
  isPending: boolean;
  resetToken: number;
}

export function EdgePanel({ nodes, edges, isDraft, onAddEdge, isPending, resetToken }: EdgePanelProps) {
  const [edgeForm, setEdgeForm] = useState({ fromNodeId: "", toNodeId: "", direction: "TWO_WAY" as EdgeDirection, maxCapacity: "" });

  useEffect(() => {
    setEdgeForm({ fromNodeId: "", toNodeId: "", direction: "TWO_WAY", maxCapacity: "" });
  }, [resetToken]);

  const nodeName = (id: number) => nodes.find((n) => n.id === id)?.code ?? id;

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onAddEdge({
      fromNodeId: Number(edgeForm.fromNodeId),
      toNodeId: Number(edgeForm.toNodeId),
      direction: edgeForm.direction,
      maxCapacity: edgeForm.maxCapacity ? Number(edgeForm.maxCapacity) : null,
    });
  };

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">통로</h2>
      {isDraft && (
        <form className="mb-3 space-y-2" onSubmit={handleSubmit}>
          <Select value={edgeForm.fromNodeId} onChange={(e) => setEdgeForm({ ...edgeForm, fromNodeId: e.target.value })} required>
            <option value="">출발 노드</option>
            {nodes.map((n) => (
              <option key={n.id} value={n.id}>
                {n.code}
              </option>
            ))}
          </Select>
          <Select value={edgeForm.toNodeId} onChange={(e) => setEdgeForm({ ...edgeForm, toNodeId: e.target.value })} required>
            <option value="">도착 노드</option>
            {nodes.map((n) => (
              <option key={n.id} value={n.id}>
                {n.code}
              </option>
            ))}
          </Select>
          <Select value={edgeForm.direction} onChange={(e) => setEdgeForm({ ...edgeForm, direction: e.target.value as EdgeDirection })}>
            {EDGE_DIRECTIONS.map((d) => (
              <option key={d} value={d}>
                {d}
              </option>
            ))}
          </Select>
          <Input
            placeholder="최대 동시 통행 수(선택)"
            type="number"
            value={edgeForm.maxCapacity}
            onChange={(e) => setEdgeForm({ ...edgeForm, maxCapacity: e.target.value })}
          />
          <Button type="submit" disabled={isPending} className="w-full">
            통로 추가
          </Button>
        </form>
      )}
      <Table>
        <thead>
          <tr>
            <Th>구간</Th>
            <Th>방향</Th>
            <Th>용량</Th>
          </tr>
        </thead>
        <tbody>
          {edges.map((edge) => (
            <tr key={edge.id}>
              <Td>
                {nodeName(edge.fromNodeId)} → {nodeName(edge.toNodeId)}
              </Td>
              <Td>{edge.direction}</Td>
              <Td>{edge.maxCapacity ?? "-"}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
