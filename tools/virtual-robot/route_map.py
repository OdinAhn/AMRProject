"""활성 지도(노드·통로) 그래프와 최단 경로.

서버의 MapRouteService와 같은 규칙을 쓴다: 통로 가중치는 distance, 없으면 두 노드의 직선 거리. ONE_WAY 통로는 from→to로만 지난다.
노드는 서버 API의 id가 아니라 VDA 5050에서 주고받는 노드 코드(code)로 다룬다.
"""

from __future__ import annotations

import heapq
import math
from dataclasses import dataclass


@dataclass(frozen=True)
class Node:
    code: str
    x: float
    y: float


class RouteMap:
    def __init__(self, map_id: str, nodes: list[Node], arcs: dict[str, dict[str, float]]):
        self.map_id = map_id
        self.nodes = {node.code: node for node in nodes}
        self._arcs = arcs

    @classmethod
    def from_api(cls, map_version_id: int, nodes: list[dict], edges: list[dict]) -> "RouteMap":
        """서버 응답(GET /api/admin/map-versions/{id}/nodes, /edges)으로 만든다."""
        by_id = {node["id"]: Node(node["code"], float(node["x"]), float(node["y"])) for node in nodes}
        arcs: dict[str, dict[str, float]] = {}

        def add(src: Node, dst: Node, weight: float) -> None:
            current = arcs.setdefault(src.code, {})
            current[dst.code] = min(weight, current.get(dst.code, math.inf))

        for edge in edges:
            src, dst = by_id.get(edge["fromNodeId"]), by_id.get(edge["toNodeId"])
            if src is None or dst is None:
                continue
            weight = float(edge["distance"]) if edge.get("distance") is not None else math.hypot(src.x - dst.x, src.y - dst.y)
            add(src, dst, weight)
            if edge.get("direction") == "TWO_WAY":
                add(dst, src, weight)
        return cls(str(map_version_id), list(by_id.values()), arcs)

    def has(self, code: str | None) -> bool:
        return code is not None and code in self.nodes

    def shortest_path(self, source: str, target: str) -> list[str] | None:
        """source부터 target까지 노드 코드 목록(양 끝 포함). 갈 수 없으면 None."""
        if not self.has(source) or not self.has(target):
            return None
        best = {source: 0.0}
        previous: dict[str, str] = {}
        queue = [(0.0, source)]
        while queue:
            distance, code = heapq.heappop(queue)
            if distance > best.get(code, math.inf):
                continue
            if code == target:
                break
            for neighbor, weight in self._arcs.get(code, {}).items():
                candidate = distance + weight
                if candidate < best.get(neighbor, math.inf):
                    best[neighbor] = candidate
                    previous[neighbor] = code
                    heapq.heappush(queue, (candidate, neighbor))
        if target not in best:
            return None
        path = [target]
        while path[-1] != source:
            path.append(previous[path[-1]])
        return list(reversed(path))
