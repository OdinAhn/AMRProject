import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Badge, Button, Card, ErrorBanner, Field, Input, PageHeader, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import { useSites } from "../../lib/useSites";
import type { CreateRobotRequest, RobotResponse } from "../../types/api";

export function RobotsPage() {
  const queryClient = useQueryClient();
  const sitesQuery = useSites();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState<string>("");
  const [error, setError] = useState<string | null>(null);

  const robotsQuery = useQuery({
    queryKey: ["robots"],
    queryFn: () => api.get<RobotResponse[]>("/api/admin/robots"),
  });

  const sites = sitesQuery.data ?? [];
  const siteCodeOf = (id: number) => sites.find((s) => s.id === id)?.code ?? id;

  const createRobot = useMutation({
    mutationFn: (body: CreateRobotRequest) => api.post<RobotResponse>("/api/admin/robots", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots"] });
      setCode("");
      setName("");
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!siteId) {
      setError("물류센터를 선택하세요.");
      return;
    }
    createRobot.mutate({ code, name, siteId: Number(siteId) });
  };

  return (
    <div>
      <PageHeader title="로봇" />
      <Card className="mb-4">
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
          <div className="w-48">
            <Field label="물류센터">
              <Select value={siteId} onChange={(e) => setSiteId(e.target.value)} required>
                <option value="">선택</option>
                {sites.map((site) => (
                  <option key={site.id} value={site.id}>
                    {site.name} ({site.code})
                  </option>
                ))}
              </Select>
            </Field>
          </div>
          <Button type="submit" disabled={createRobot.isPending}>
            등록
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        {robotsQuery.isLoading && <Spinner />}
        {robotsQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>코드</Th>
                <Th>이름</Th>
                <Th>물류센터</Th>
                <Th>운영 상태</Th>
                <Th>설정(요청/적용)</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {robotsQuery.data.map((robot) => (
                <tr key={robot.id}>
                  <Td>{robot.code}</Td>
                  <Td>{robot.name}</Td>
                  <Td>{siteCodeOf(robot.siteId)}</Td>
                  <Td>
                    <Badge tone={robot.lifecycleStatus === "IN_SERVICE" ? "green" : "gray"}>{robot.lifecycleStatus}</Badge>
                  </Td>
                  <Td>
                    v{robot.desiredConfigVersion ?? "-"} / v{robot.appliedConfigVersion ?? "-"}
                  </Td>
                  <Td>
                    <Link to={`/robots/${robot.id}`} className="text-indigo-600 hover:underline">
                      상세
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
