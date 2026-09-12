import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { SiteCreateForm } from "../../components/sites/SiteCreateForm";
import { SiteTable } from "../../components/sites/SiteTable";
import { PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateSiteRequest, SiteResponse } from "../../types/api";

export function SitesPage() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

  const sitesQuery = useQuery({
    queryKey: ["sites"],
    queryFn: () => api.get<SiteResponse[]>("/api/admin/sites"),
  });

  const createSite = useMutation({
    mutationFn: (body: CreateSiteRequest) => api.post<SiteResponse>("/api/admin/sites", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sites"] });
      setResetToken((t) => t + 1);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  return (
    <div>
      <PageHeader title="물류센터" />
      <SiteCreateForm onSubmit={(body) => createSite.mutate(body)} isPending={createSite.isPending} error={error} resetToken={resetToken} />

      {sitesQuery.isLoading && <Spinner />}
      {sitesQuery.data && <SiteTable sites={sitesQuery.data} />}
    </div>
  );
}
