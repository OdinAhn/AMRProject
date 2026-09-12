import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, Input, Select, Table, Td, Th } from "../ui";
import type { CreateNodeRequest, NodeResponse, NodeType } from "../../types/api";

const NODE_TYPES: NodeType[] = ["WAYPOINT", "STATION", "CHARGING"];

interface NodePanelProps {
  nodes: NodeResponse[];
  isDraft: boolean;
  onAddNode: (body: CreateNodeRequest) => void;
  isPending: boolean;
  resetToken: number;
}

export function NodePanel({ nodes, isDraft, onAddNode, isPending, resetToken }: NodePanelProps) {
  const [nodeForm, setNodeForm] = useState({ code: "", x: "0", y: "0", nodeType: "WAYPOINT" as NodeType });

  useEffect(() => {
    setNodeForm({ code: "", x: "0", y: "0", nodeType: "WAYPOINT" });
  }, [resetToken]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onAddNode({ code: nodeForm.code, x: Number(nodeForm.x), y: Number(nodeForm.y), nodeType: nodeForm.nodeType });
  };

  return (
    <Card>
      <h2 className="mb-3 text-sm font-semibold text-gray-700">노드</h2>
      {isDraft && (
        <form className="mb-3 space-y-2" onSubmit={handleSubmit}>
          <Input placeholder="코드" value={nodeForm.code} onChange={(e) => setNodeForm({ ...nodeForm, code: e.target.value })} required />
          <div className="flex gap-2">
            <Input placeholder="x" type="number" value={nodeForm.x} onChange={(e) => setNodeForm({ ...nodeForm, x: e.target.value })} />
            <Input placeholder="y" type="number" value={nodeForm.y} onChange={(e) => setNodeForm({ ...nodeForm, y: e.target.value })} />
          </div>
          <Select value={nodeForm.nodeType} onChange={(e) => setNodeForm({ ...nodeForm, nodeType: e.target.value as NodeType })}>
            {NODE_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </Select>
          <Button type="submit" disabled={isPending} className="w-full">
            노드 추가
          </Button>
        </form>
      )}
      <Table>
        <thead>
          <tr>
            <Th>코드</Th>
            <Th>좌표</Th>
            <Th>유형</Th>
          </tr>
        </thead>
        <tbody>
          {nodes.map((n) => (
            <tr key={n.id}>
              <Td>{n.code}</Td>
              <Td>
                ({n.x}, {n.y})
              </Td>
              <Td>{n.nodeType}</Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
