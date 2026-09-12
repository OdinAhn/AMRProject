import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Badge, Button, Card, ErrorBanner, Field, Input, PageHeader, Spinner, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateSiteRequest, SiteResponse } from "../../types/api";

export function SitesPage() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const sitesQuery = useQuery({
    queryKey: ["sites"],
    queryFn: () => api.get<SiteResponse[]>("/api/admin/sites"),
  });

  const createSite = useMutation({
    mutationFn: (body: CreateSiteRequest) => api.post<SiteResponse>("/api/admin/sites", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["sites"] });
      setCode("");
      setName("");
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createSite.mutate({ code, name });
  };

  return (
    <div>
      <PageHeader title="물류센터" />
      <Card className="mb-4">
        <form onSubmit={onSubmit} className="flex items-end gap-3">
          <div className="w-40">
            <Field label="코드">
              <Input value={code} onChange={(e) => setCode(e.target.value)} required />
            </Field>
          </div>
          <div className="flex-1">
            <Field label="이름">
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </Field>
          </div>
          <Button type="submit" disabled={createSite.isPending}>
            생성
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        {sitesQuery.isLoading && <Spinner />}
        {sitesQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>코드</Th>
                <Th>이름</Th>
                <Th>상태</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {sitesQuery.data.map((site) => (
                <tr key={site.id}>
                  <Td>{site.code}</Td>
                  <Td>{site.name}</Td>
                  <Td>
                    <Badge tone={site.status === "ACTIVE" ? "green" : "gray"}>{site.status}</Badge>
                  </Td>
                  <Td>
                    <Link to={`/sites/${site.id}`} className="text-indigo-600 hover:underline">
                      작업장 관리
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
