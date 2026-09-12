import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Input,
  PageHeader,
  Select,
  Table,
  Td,
  Th,
} from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type {
  CreateEdgeRequest,
  CreateNodeRequest,
  CreateZoneRequest,
  EdgeDirection,
  EdgeResponse,
  MapVersionResponse,
  MapVersionValidationResponse,
  NodeResponse,
  NodeType,
  ZoneResponse,
  ZoneType,
} from "../../types/api";

const NODE_TYPES: NodeType[] = ["WAYPOINT", "STATION", "CHARGING"];
const EDGE_DIRECTIONS: EdgeDirection[] = ["ONE_WAY", "TWO_WAY"];
const ZONE_TYPES: ZoneType[] = ["MUTEX_ZONE", "SAFETY_ZONE"];

export function MapVersionDetailPage() {
  const { mapId, versionId } = useParams<{ mapId: string; versionId: string }>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [validation, setValidation] = useState<MapVersionValidationResponse | null>(null);

  const [nodeForm, setNodeForm] = useState({ code: "", x: "0", y: "0", nodeType: "WAYPOINT" as NodeType });
  const [edgeForm, setEdgeForm] = useState({ fromNodeId: "", toNodeId: "", direction: "TWO_WAY" as EdgeDirection, maxCapacity: "" });
  const [zoneForm, setZoneForm] = useState({ code: "", zoneType: "MUTEX_ZONE" as ZoneType, nodeIds: [] as string[] });

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ["map-versions", versionId] });
  };

  const versionQuery = useQuery({
    queryKey: ["map-versions", versionId],
    queryFn: () => api.get<MapVersionResponse>(`/api/admin/map-versions/${versionId}`),
  });
  const nodesQuery = useQuery({
    queryKey: ["map-versions", versionId, "nodes"],
    queryFn: () => api.get<NodeResponse[]>(`/api/admin/map-versions/${versionId}/nodes`),
  });
  const edgesQuery = useQuery({
    queryKey: ["map-versions", versionId, "edges"],
    queryFn: () => api.get<EdgeResponse[]>(`/api/admin/map-versions/${versionId}/edges`),
  });
  const zonesQuery = useQuery({
    queryKey: ["map-versions", versionId, "zones"],
    queryFn: () => api.get<ZoneResponse[]>(`/api/admin/map-versions/${versionId}/zones`),
  });

  const isDraft = versionQuery.data?.status === "DRAFT";
  const nodes = nodesQuery.data ?? [];

  const addNode = useMutation({
    mutationFn: (body: CreateNodeRequest) => api.post<NodeResponse>(`/api/admin/map-versions/${versionId}/nodes`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "nodes"] });
      invalidateAll();
      setNodeForm({ code: "", x: "0", y: "0", nodeType: "WAYPOINT" });
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "노드 추가에 실패했습니다."),
  });

  const addEdge = useMutation({
    mutationFn: (body: CreateEdgeRequest) => api.post<EdgeResponse>(`/api/admin/map-versions/${versionId}/edges`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "edges"] });
      invalidateAll();
      setEdgeForm({ fromNodeId: "", toNodeId: "", direction: "TWO_WAY", maxCapacity: "" });
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "통로 추가에 실패했습니다."),
  });

  const addZone = useMutation({
    mutationFn: (body: CreateZoneRequest) => api.post<ZoneResponse>(`/api/admin/map-versions/${versionId}/zones`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "zones"] });
      invalidateAll();
      setZoneForm({ code: "", zoneType: "MUTEX_ZONE", nodeIds: [] });
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "구역 추가에 실패했습니다."),
  });

  const validate = useMutation({
    mutationFn: () => api.post<MapVersionValidationResponse>(`/api/admin/map-versions/${versionId}/validate`),
    onSuccess: (result) => {
      setValidation(result);
      invalidateAll();
    },
  });

  const activate = useMutation({
    mutationFn: () => api.post<MapVersionResponse>(`/api/admin/map-versions/${versionId}/activate`),
    onSuccess: () => invalidateAll(),
    onError: (err) => setError(err instanceof ApiError ? err.message : "활성화에 실패했습니다."),
  });

  const nodeName = (id: number) => nodes.find((n) => n.id === id)?.code ?? id;

  return (
    <div>
      <PageHeader
        title={versionQuery.data ? `지도 버전 v${versionQuery.data.versionNo}` : "지도 버전"}
        actions={
          <Link to={`/maps/${mapId}`} className="text-sm text-indigo-600 hover:underline">
            ← 버전 목록
          </Link>
        }
      />

      {versionQuery.data && (
        <Card className="mb-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-500">상태</span>
              <Badge tone={versionQuery.data.status === "ACTIVE" ? "green" : "yellow"}>{versionQuery.data.status}</Badge>
            </div>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => validate.mutate()} disabled={!isDraft || validate.isPending}>
                검증
              </Button>
              <Button
                onClick={() => activate.mutate()}
                disabled={versionQuery.data.status !== "VALIDATED" || activate.isPending}
              >
                활성화
              </Button>
            </div>
          </div>
          {validation && (
            <div className="mt-3">
              {validation.valid ? (
                <p className="text-sm text-green-700">검증 통과 — 활성화할 수 있습니다.</p>
              ) : (
                <ul className="list-disc pl-5 text-sm text-red-700">
                  {validation.errors.map((e, i) => (
                    <li key={i}>{e}</li>
                  ))}
                </ul>
              )}
            </div>
          )}
          <ErrorBanner message={error} />
        </Card>
      )}

      <div className="grid grid-cols-3 gap-4">
        <Card>
          <h2 className="mb-3 text-sm font-semibold text-gray-700">노드</h2>
          {isDraft && (
            <form
              className="mb-3 space-y-2"
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                addNode.mutate({ code: nodeForm.code, x: Number(nodeForm.x), y: Number(nodeForm.y), nodeType: nodeForm.nodeType });
              }}
            >
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
              <Button type="submit" disabled={addNode.isPending} className="w-full">
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

        <Card>
          <h2 className="mb-3 text-sm font-semibold text-gray-700">통로</h2>
          {isDraft && (
            <form
              className="mb-3 space-y-2"
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                addEdge.mutate({
                  fromNodeId: Number(edgeForm.fromNodeId),
                  toNodeId: Number(edgeForm.toNodeId),
                  direction: edgeForm.direction,
                  maxCapacity: edgeForm.maxCapacity ? Number(edgeForm.maxCapacity) : null,
                });
              }}
            >
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
              <Button type="submit" disabled={addEdge.isPending} className="w-full">
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
              {(edgesQuery.data ?? []).map((edge) => (
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

        <Card>
          <h2 className="mb-3 text-sm font-semibold text-gray-700">구역</h2>
          {isDraft && (
            <form
              className="mb-3 space-y-2"
              onSubmit={(e: FormEvent) => {
                e.preventDefault();
                addZone.mutate({ code: zoneForm.code, zoneType: zoneForm.zoneType, nodeIds: zoneForm.nodeIds.map(Number) });
              }}
            >
              <Input placeholder="코드" value={zoneForm.code} onChange={(e) => setZoneForm({ ...zoneForm, code: e.target.value })} required />
              <Select value={zoneForm.zoneType} onChange={(e) => setZoneForm({ ...zoneForm, zoneType: e.target.value as ZoneType })}>
                {ZONE_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
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
              <Button type="submit" disabled={addZone.isPending} className="w-full">
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
              {(zonesQuery.data ?? []).map((zone) => (
                <tr key={zone.id}>
                  <Td>{zone.code}</Td>
                  <Td>{zone.zoneType}</Td>
                  <Td>{zone.nodeIds.map(nodeName).join(", ")}</Td>
                </tr>
              ))}
            </tbody>
          </Table>
        </Card>
      </div>
    </div>
  );
}
