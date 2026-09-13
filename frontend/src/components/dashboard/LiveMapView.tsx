import { useQuery } from "@tanstack/react-query";
import { api } from "../../lib/apiClient";
import { useRobotPositions } from "../../lib/eventStream";
import { Card } from "../ui";
import type { EdgeResponse, NodeResponse, ObstacleResponse, RobotSummary } from "../../types/api";

const VIEW_SIZE = 100;
const NODE_RADIUS = 1.2;
const ROBOT_RADIUS = 2.4;
/** 이 시간보다 오래된 표시용 좌표는 무시하고 노드 위치를 쓴다 — 애니메이션이 끝났거나 연결이 끊긴 경우. */
const POSITION_TTL_MS = 3000;

interface LiveMapViewProps {
  mapVersionId: number;
  robots: RobotSummary[];
}

function robotColor(robot: RobotSummary) {
  if (robot.connectionState !== "ONLINE") return "#dc2626";
  if (robot.currentTaskId) return "#2563eb";
  return "#16a34a";
}

/**
 * 30단계: 활성 지도 위에 로봇 위치를 보여주는 읽기 전용 지도. 25단계 편집기(MapCanvasEditor)와 같은 좌표계(100×100)를 쓴다.
 * 위치는 두 가지를 합친다 — 로봇이 마지막으로 도착한 노드(개요 API의 currentNodeId)와, 시뮬레이터가 이동 중에 흘려보내는
 * 표시용 좌표(robot.position 이벤트). 표시용 좌표가 최근에 왔으면 그걸 쓰고, 아니면 노드 위치에 놓는다.
 */
export function LiveMapView({ mapVersionId, robots }: LiveMapViewProps) {
  const positions = useRobotPositions();
  const nodesQuery = useQuery({
    queryKey: ["map-versions", String(mapVersionId), "nodes"],
    queryFn: () => api.get<NodeResponse[]>(`/api/admin/map-versions/${mapVersionId}/nodes`),
  });
  const edgesQuery = useQuery({
    queryKey: ["map-versions", String(mapVersionId), "edges"],
    queryFn: () => api.get<EdgeResponse[]>(`/api/admin/map-versions/${mapVersionId}/edges`),
  });
  const obstaclesQuery = useQuery({
    queryKey: ["map-versions", String(mapVersionId), "obstacles"],
    queryFn: () => api.get<ObstacleResponse[]>(`/api/admin/map-versions/${mapVersionId}/obstacles`),
  });

  const nodes = nodesQuery.data ?? [];
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const now = Date.now();

  const placedRobots = robots.flatMap((robot) => {
    const live = positions[robot.robotId];
    if (live && live.mapVersionId === mapVersionId && now - live.receivedAt < POSITION_TTL_MS) {
      return [{ robot, x: live.x, y: live.y, moving: !live.arrived }];
    }
    const node = robot.currentNodeId ? nodeById.get(robot.currentNodeId) : undefined;
    return node ? [{ robot, x: node.x, y: node.y, moving: false }] : [];
  });
  const unplacedCount = robots.length - placedRobots.length;

  return (
    <Card>
      <div className="mb-2 flex items-center justify-between">
        <h2 className="text-sm font-semibold text-gray-700">실시간 지도</h2>
        <div className="flex items-center gap-3 text-xs text-gray-500">
          <Legend color="#16a34a" label="대기" />
          <Legend color="#2563eb" label="작업 중" />
          <Legend color="#dc2626" label="오프라인" />
        </div>
      </div>
      <svg viewBox={`0 0 ${VIEW_SIZE} ${VIEW_SIZE}`} className="aspect-square w-full rounded-md border border-gray-200 bg-gray-50">
        {(obstaclesQuery.data ?? []).map((obstacle) => (
          <rect key={obstacle.id} x={obstacle.x} y={obstacle.y} width={obstacle.width} height={obstacle.height} fill="#d1d5db" stroke="#9ca3af" strokeWidth={0.2} />
        ))}
        {(edgesQuery.data ?? []).map((edge) => {
          const from = nodeById.get(edge.fromNodeId);
          const to = nodeById.get(edge.toNodeId);
          if (!from || !to) return null;
          return <line key={edge.id} x1={from.x} y1={from.y} x2={to.x} y2={to.y} stroke="#cbd5e1" strokeWidth={0.6} />;
        })}
        {nodes.map((node) => (
          <g key={node.id}>
            <circle cx={node.x} cy={node.y} r={NODE_RADIUS} fill="#94a3b8" />
            <text x={node.x} y={node.y + NODE_RADIUS + 2.6} fontSize={2.4} textAnchor="middle" fill="#64748b">
              {node.code}
            </text>
          </g>
        ))}
        {placedRobots.map(({ robot, x, y, moving }) => (
          <g key={robot.robotId} style={{ transform: `translate(${x}px, ${y}px)`, transition: "transform 200ms linear" }}>
            {moving && <circle r={ROBOT_RADIUS + 1.2} fill={robotColor(robot)} opacity={0.2} />}
            <circle r={ROBOT_RADIUS} fill={robotColor(robot)} stroke="white" strokeWidth={0.5} />
            <text y={-ROBOT_RADIUS - 1} fontSize={2.8} fontWeight={600} textAnchor="middle" fill="#111827">
              {robot.code}
            </text>
          </g>
        ))}
      </svg>
      {unplacedCount > 0 && (
        <p className="mt-2 text-xs text-gray-500">
          위치를 모르는 로봇 {unplacedCount}대 — 아직 이동한 적이 없거나, 목적지 작업장이 이 지도의 노드에 연결되지 않았습니다.
        </p>
      )}
    </Card>
  );
}

function Legend({ color, label }: { color: string; label: string }) {
  return (
    <span className="flex items-center gap-1">
      <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
      {label}
    </span>
  );
}
