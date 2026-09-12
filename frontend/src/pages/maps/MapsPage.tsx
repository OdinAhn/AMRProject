import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Button, Card, ErrorBanner, Field, Input, PageHeader, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { CreateMapRequest, MapResponse } from "../../types/api";

export function MapsPage() {
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState<string>("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const sites = sitesQuery.data ?? [];
  const effectiveSiteId = siteId || (sites[0] ? String(sites[0].id) : "");

  const mapsQuery = useQuery({
    queryKey: ["sites", effectiveSiteId, "maps"],
    queryFn: () => api.get<MapResponse[]>(`/api/admin/sites/${effectiveSiteId}/maps`),
    enabled: !!effectiveSiteId,
  });

  const createMap = useMutation({
    mutationFn: (body: CreateMapRequest) => api.post<MapResponse>(`/api/admin/sites/${effectiveSiteId}/maps`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sites", effectiveSiteId, "maps"] });
      setName("");
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMap.mutate({ name });
  };

  return (
    <div>
      <PageHeader title="지도" />

      <Card className="mb-4">
        <Field label="물류센터">
          <Select value={effectiveSiteId} onChange={(e) => setSiteId(e.target.value)}>
            {sites.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name} ({site.code})
              </option>
            ))}
          </Select>
        </Field>
      </Card>

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">지도 생성</h2>
        <form onSubmit={onSubmit} className="flex items-end gap-3">
          <div className="flex-1">
            <Field label="이름">
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </Field>
          </div>
          <Button type="submit" disabled={createMap.isPending || !effectiveSiteId}>
            생성
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        {mapsQuery.isLoading && <Spinner />}
        {mapsQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>이름</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {mapsQuery.data.map((map) => (
                <tr key={map.id}>
                  <Td>{map.name}</Td>
                  <Td>
                    <Link to={`/maps/${map.id}`} className="text-indigo-600 hover:underline">
                      버전 관리
                    </Link>
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
