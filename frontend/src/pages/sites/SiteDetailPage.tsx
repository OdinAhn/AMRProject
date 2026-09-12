import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Field,
  Input,
  PageHeader,
  Select,
  Spinner,
  Table,
  Td,
  Th,
} from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateStationRequest, SiteResponse, StationResponse, StationType } from "../../types/api";

const STATION_TYPES: StationType[] = ["PICKUP", "DROPOFF", "WAITING", "CHARGING", "GENERIC"];

export function SiteDetailPage() {
  const { siteId } = useParams<{ siteId: string }>();
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [stationType, setStationType] = useState<StationType>("GENERIC");
  const [error, setError] = useState<string | null>(null);

  const siteQuery = useQuery({
    queryKey: ["sites", siteId],
    queryFn: () => api.get<SiteResponse>(`/api/admin/sites/${siteId}`),
  });

  const stationsQuery = useQuery({
    queryKey: ["sites", siteId, "stations"],
    queryFn: () => api.get<StationResponse[]>(`/api/admin/sites/${siteId}/stations`),
  });

  const createStation = useMutation({
    mutationFn: (body: CreateStationRequest) => api.post<StationResponse>(`/api/admin/sites/${siteId}/stations`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sites", siteId, "stations"] });
      setCode("");
      setName("");
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const updateCapacity = useMutation({
    mutationFn: ({ stationId, maxConcurrentRobots }: { stationId: number; maxConcurrentRobots: number | null }) =>
      api.patch(`/api/admin/stations/${stationId}`, { maxConcurrentRobots }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["sites", siteId, "stations"] }),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createStation.mutate({ code, name, stationType });
  };

  return (
    <div>
      <PageHeader
        title={siteQuery.data ? `${siteQuery.data.name} (${siteQuery.data.code})` : "작업장 관리"}
        actions={
          <Link to="/sites" className="text-sm text-indigo-600 hover:underline">
            ← 물류센터 목록
          </Link>
        }
      />

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">작업장 추가</h2>
        <form onSubmit={onSubmit} className="flex items-end gap-3">
          <div className="w-32">
            <Field label="코드">
              <Input value={code} onChange={(e) => setCode(e.target.value)} required />
            </Field>
          </div>
          <div className="flex-1">
            <Field label="이름">
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </Field>
          </div>
          <div className="w-40">
            <Field label="유형">
              <Select value={stationType} onChange={(e) => setStationType(e.target.value as StationType)}>
                {STATION_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" disabled={createStation.isPending}>
            추가
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        {stationsQuery.isLoading && <Spinner />}
        {stationsQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>코드</Th>
                <Th>이름</Th>
                <Th>유형</Th>
                <Th>상태</Th>
                <Th>동시 최대 로봇 수</Th>
              </tr>
            </thead>
            <tbody>
              {stationsQuery.data.map((station) => (
                <tr key={station.id}>
                  <Td>{station.code}</Td>
                  <Td>{station.name}</Td>
                  <Td>
                    <Badge tone="blue">{station.stationType}</Badge>
                  </Td>
                  <Td>{station.status}</Td>
                  <Td>
                    <input
                      type="number"
                      min={0}
                      defaultValue={station.maxConcurrentRobots ?? ""}
                      placeholder="무제한"
                      className="w-24 rounded-md border border-gray-300 px-2 py-1 text-sm"
                      onBlur={(e) => {
                        const value = e.target.value === "" ? null : Number(e.target.value);
                        if (value !== station.maxConcurrentRobots) {
                          updateCapacity.mutate({ stationId: station.id, maxConcurrentRobots: value });
                        }
                      }}
                    />
                  </Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>
    </div>
  );
}
