import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { MapCanvasEditor } from "../../components/maps/MapCanvasEditor";
import { MapVersionHeaderCard } from "../../components/maps/MapVersionHeaderCard";
import { ZonePanel } from "../../components/maps/ZonePanel";
import { PageHeader } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type {
  CreateEdgeRequest,
  CreateNodeRequest,
  CreateObstacleRequest,
  CreateZoneRequest,
  EdgeResponse,
  MapVersionResponse,
  MapVersionValidationResponse,
  NodeResponse,
  ObstacleResponse,
  ZoneResponse,
} from "../../types/api";

export function MapVersionDetailPage() {
  const { mapId, versionId } = useParams<{ mapId: string; versionId: string }>();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [validation, setValidation] = useState<MapVersionValidationResponse | null>(null);
  const [zoneResetToken, setZoneResetToken] = useState(0);

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
  const obstaclesQuery = useQuery({
    queryKey: ["map-versions", versionId, "obstacles"],
    queryFn: () => api.get<ObstacleResponse[]>(`/api/admin/map-versions/${versionId}/obstacles`),
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
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "노드 추가에 실패했습니다."),
  });

  const addEdge = useMutation({
    mutationFn: (body: CreateEdgeRequest) => api.post<EdgeResponse>(`/api/admin/map-versions/${versionId}/edges`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "edges"] });
      invalidateAll();
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "통로 추가에 실패했습니다."),
  });

  const addObstacle = useMutation({
    mutationFn: (body: CreateObstacleRequest) => api.post<ObstacleResponse>(`/api/admin/map-versions/${versionId}/obstacles`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "obstacles"] });
      invalidateAll();
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "장애물 추가에 실패했습니다."),
  });

  const addZone = useMutation({
    mutationFn: (body: CreateZoneRequest) => api.post<ZoneResponse>(`/api/admin/map-versions/${versionId}/zones`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["map-versions", versionId, "zones"] });
      invalidateAll();
      setZoneResetToken((t) => t + 1);
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
        <MapVersionHeaderCard
          version={versionQuery.data}
          isDraft={!!isDraft}
          onValidate={() => validate.mutate()}
          isValidating={validate.isPending}
          onActivate={() => activate.mutate()}
          isActivating={activate.isPending}
          validation={validation}
          error={error}
        />
      )}

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2">
          <MapCanvasEditor
            nodes={nodes}
            edges={edgesQuery.data ?? []}
            obstacles={obstaclesQuery.data ?? []}
            isDraft={!!isDraft}
            onAddNode={(body) => addNode.mutate(body)}
            onAddEdge={(body) => addEdge.mutate(body)}
            onAddObstacle={(body) => addObstacle.mutate(body)}
            isPending={addNode.isPending || addEdge.isPending || addObstacle.isPending}
          />
        </div>
        <ZonePanel
          nodes={nodes}
          zones={zonesQuery.data ?? []}
          isDraft={!!isDraft}
          onAddZone={(body) => addZone.mutate(body)}
          isPending={addZone.isPending}
          resetToken={zoneResetToken}
        />
      </div>
    </div>
  );
}
