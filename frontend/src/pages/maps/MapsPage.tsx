import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { MapCreateForm } from "../../components/maps/MapCreateForm";
import { MapTable } from "../../components/maps/MapTable";
import { Field, PageHeader, Select, Spinner, Card } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { CreateMapRequest, MapResponse } from "../../types/api";

export function MapsPage() {
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [siteId, setSiteId] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

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
      setResetToken((t) => t + 1);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

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

      <MapCreateForm
        onSubmit={(name) => createMap.mutate({ name })}
        isPending={createMap.isPending}
        disabled={!effectiveSiteId}
        error={error}
        resetToken={resetToken}
      />

      {mapsQuery.isLoading && <Spinner />}
      {mapsQuery.data && <MapTable maps={mapsQuery.data} />}
    </div>
  );
}
