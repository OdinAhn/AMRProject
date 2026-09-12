import { useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { Button, Card, Field, Input, Select } from "../ui";
import type {
  CreateEdgeRequest,
  CreateNodeRequest,
  CreateObstacleRequest,
  EdgeDirection,
  EdgeResponse,
  NodeResponse,
  NodeType,
  ObstacleResponse,
} from "../../types/api";

const NODE_TYPES: NodeType[] = ["WAYPOINT", "STATION", "CHARGING"];
const EDGE_DIRECTIONS: EdgeDirection[] = ["ONE_WAY", "TWO_WAY"];
const NODE_COLOR: Record<NodeType, string> = {
  WAYPOINT: "#6366f1",
  STATION: "#16a34a",
  CHARGING: "#f59e0b",
};

const VIEW_SIZE = 100;
const PIXEL_SIZE = 560;
const NODE_RADIUS = 1.6;
const MIN_OBSTACLE_SIZE = 1;

type Mode = "node" | "obstacle" | "edge";

interface MapCanvasEditorProps {
  nodes: NodeResponse[];
  edges: EdgeResponse[];
  obstacles: ObstacleResponse[];
  isDraft: boolean;
  onAddNode: (request: CreateNodeRequest) => void;
  onAddEdge: (request: CreateEdgeRequest) => void;
  onAddObstacle: (request: CreateObstacleRequest) => void;
  isPending: boolean;
}

export function MapCanvasEditor({
  nodes,
  edges,
  obstacles,
  isDraft,
  onAddNode,
  onAddEdge,
  onAddObstacle,
  isPending,
}: MapCanvasEditorProps) {
  const svgRef = useRef<SVGSVGElement>(null);
  const [mode, setMode] = useState<Mode>("node");
  const [nodeType, setNodeType] = useState<NodeType>("WAYPOINT");
  const [nodeCode, setNodeCode] = useState("N1");
  const [edgeDirection, setEdgeDirection] = useState<EdgeDirection>("TWO_WAY");
  const [fromNodeId, setFromNodeId] = useState<number | null>(null);
  const [dragStart, setDragStart] = useState<{ x: number; y: number } | null>(null);
  const [dragCurrent, setDragCurrent] = useState<{ x: number; y: number } | null>(null);

  const toMapPoint = (e: ReactMouseEvent): { x: number; y: number } | null => {
    const svg = svgRef.current;
    if (!svg) return null;
    const point = svg.createSVGPoint();
    point.x = e.clientX;
    point.y = e.clientY;
    const ctm = svg.getScreenCTM();
    if (!ctm) return null;
    const transformed = point.matrixTransform(ctm.inverse());
    return { x: transformed.x, y: transformed.y };
  };

  const handleCanvasClick = (e: ReactMouseEvent) => {
    if (!isDraft || mode !== "node") return;
    const point = toMapPoint(e);
    if (!point || !nodeCode.trim()) return;
    onAddNode({ code: nodeCode.trim(), x: round(point.x), y: round(point.y), nodeType });
    setNodeCode(nextCode(nodeCode));
  };

  const handleNodeClick = (node: NodeResponse) => {
    if (!isDraft || mode !== "edge") return;
    if (fromNodeId === null) {
      setFromNodeId(node.id);
      return;
    }
    if (fromNodeId === node.id) {
      setFromNodeId(null);
      return;
    }
    onAddEdge({ fromNodeId, toNodeId: node.id, direction: edgeDirection });
    setFromNodeId(null);
  };

  const handleMouseDown = (e: ReactMouseEvent) => {
    if (!isDraft || mode !== "obstacle") return;
    const point = toMapPoint(e);
    if (!point) return;
    setDragStart(point);
    setDragCurrent(point);
  };

  const handleMouseMove = (e: ReactMouseEvent) => {
    if (!dragStart) return;
    const point = toMapPoint(e);
    if (point) setDragCurrent(point);
  };

  const handleMouseUp = () => {
    if (!dragStart || !dragCurrent) {
      setDragStart(null);
      setDragCurrent(null);
      return;
    }
    const x = Math.min(dragStart.x, dragCurrent.x);
    const y = Math.min(dragStart.y, dragCurrent.y);
    const width = Math.abs(dragCurrent.x - dragStart.x);
    const height = Math.abs(dragCurrent.y - dragStart.y);
    setDragStart(null);
    setDragCurrent(null);
    if (width < MIN_OBSTACLE_SIZE || height < MIN_OBSTACLE_SIZE) return;
    onAddObstacle({ x: round(x), y: round(y), width: round(width), height: round(height) });
  };

  const nodeById = (id: number) => nodes.find((n) => n.id === id);
  const previewRect =
    dragStart && dragCurrent
      ? {
          x: Math.min(dragStart.x, dragCurrent.x),
          y: Math.min(dragStart.y, dragCurrent.y),
          width: Math.abs(dragCurrent.x - dragStart.x),
          height: Math.abs(dragCurrent.y - dragStart.y),
        }
      : null;

  return (
    <Card>
      {isDraft && (
        <div className="mb-3 flex flex-wrap items-end gap-3">
          <div className="flex gap-2">
            <Button variant={mode === "node" ? "primary" : "secondary"} onClick={() => setMode("node")}>
              노드 추가
            </Button>
            <Button variant={mode === "obstacle" ? "primary" : "secondary"} onClick={() => setMode("obstacle")}>
              장애물 추가
            </Button>
            <Button
              variant={mode === "edge" ? "primary" : "secondary"}
              onClick={() => {
                setMode("edge");
                setFromNodeId(null);
              }}
            >
              통로 연결
            </Button>
          </div>

          {mode === "node" && (
            <>
              <div className="w-28">
                <Field label="코드">
                  <Input value={nodeCode} onChange={(e) => setNodeCode(e.target.value)} />
                </Field>
              </div>
              <div className="w-36">
                <Field label="유형">
                  <Select value={nodeType} onChange={(e) => setNodeType(e.target.value as NodeType)}>
                    {NODE_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <p className="pb-2 text-xs text-gray-500">캔버스를 클릭해 노드를 놓으세요.</p>
            </>
          )}

          {mode === "obstacle" && <p className="pb-2 text-xs text-gray-500">캔버스에서 드래그해 장애물 사각형을 그리세요.</p>}

          {mode === "edge" && (
            <>
              <div className="w-36">
                <Field label="방향">
                  <Select value={edgeDirection} onChange={(e) => setEdgeDirection(e.target.value as EdgeDirection)}>
                    {EDGE_DIRECTIONS.map((d) => (
                      <option key={d} value={d}>
                        {d}
                      </option>
                    ))}
                  </Select>
                </Field>
              </div>
              <p className="pb-2 text-xs text-gray-500">
                {fromNodeId === null ? "출발 노드를 클릭하세요." : "도착 노드를 클릭하세요(같은 노드를 다시 누르면 취소)."}
              </p>
            </>
          )}
        </div>
      )}

      <svg
        ref={svgRef}
        viewBox={`0 0 ${VIEW_SIZE} ${VIEW_SIZE}`}
        width={PIXEL_SIZE}
        height={PIXEL_SIZE}
        className="rounded-md border border-gray-300 bg-gray-50"
        style={{ cursor: isDraft && mode !== "edge" ? "crosshair" : "default" }}
        onClick={handleCanvasClick}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={() => {
          setDragStart(null);
          setDragCurrent(null);
        }}
      >
        {obstacles.map((obstacle) => (
          <rect
            key={obstacle.id}
            x={obstacle.x}
            y={obstacle.y}
            width={obstacle.width}
            height={obstacle.height}
            fill="#9ca3af"
            stroke="#4b5563"
            strokeWidth={0.2}
          />
        ))}

        {previewRect && (
          <rect
            x={previewRect.x}
            y={previewRect.y}
            width={previewRect.width}
            height={previewRect.height}
            fill="#9ca3af"
            fillOpacity={0.4}
            stroke="#4b5563"
            strokeWidth={0.2}
            strokeDasharray="1"
          />
        )}

        {edges.map((edge) => {
          const from = nodeById(edge.fromNodeId);
          const to = nodeById(edge.toNodeId);
          if (!from || !to) return null;
          return <line key={edge.id} x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke="#94a3b8" strokeWidth={0.5} />;
        })}

        {nodes.map((node) => (
          <g key={node.id} onClick={(e) => (mode === "edge" ? (e.stopPropagation(), handleNodeClick(node)) : undefined)}>
            <circle
              cx={node.x}
              cy={node.y}
              r={NODE_RADIUS}
              fill={NODE_COLOR[node.nodeType]}
              stroke={fromNodeId === node.id ? "#111827" : "white"}
              strokeWidth={fromNodeId === node.id ? 0.6 : 0.3}
              style={{ cursor: mode === "edge" ? "pointer" : "inherit" }}
            />
            <text x={node.x} y={node.y - NODE_RADIUS - 0.6} fontSize={2.6} textAnchor="middle" fill="#374151">
              {node.code}
            </text>
          </g>
        ))}
      </svg>

      {isPending && <p className="mt-2 text-xs text-gray-500">저장 중...</p>}
    </Card>
  );
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}

function nextCode(code: string): string {
  const match = code.match(/^(.*?)(\d+)$/);
  if (!match) return code;
  const [, prefix, digits] = match;
  const next = String(Number(digits) + 1).padStart(digits.length, "0");
  return prefix + next;
}
