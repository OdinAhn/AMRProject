import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { RobotCreateForm } from "../../components/robots/RobotCreateForm";
import { RobotTable } from "../../components/robots/RobotTable";
import { PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { CreateRobotRequest, RobotResponse } from "../../types/api";

export function RobotsPage() {
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

  const robotsQuery = useQuery({
    queryKey: ["robots"],
    queryFn: () => api.get<RobotResponse[]>("/api/admin/robots"),
  });

  const sites = sitesQuery.data ?? [];

  const createRobot = useMutation({
    mutationFn: (body: CreateRobotRequest) => api.post<RobotResponse>("/api/admin/robots", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots"] });
      setResetToken((t) => t + 1);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  return (
    <div>
      <PageHeader title="로봇" />
      <RobotCreateForm
        sites={sites}
        onSubmit={(body) => createRobot.mutate(body)}
        isPending={createRobot.isPending}
        error={error}
        resetToken={resetToken}
      />

      {robotsQuery.isLoading && <Spinner />}
      {robotsQuery.data && <RobotTable robots={robotsQuery.data} sites={sites} />}
    </div>
  );
}
