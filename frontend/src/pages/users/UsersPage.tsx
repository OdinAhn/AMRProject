import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Badge, Button, Card, ErrorBanner, Field, Input, PageHeader, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateUserRequest, UserResponse } from "../../types/api";

export function UsersPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ username: "", password: "", displayName: "", roleCode: "WORKER" });
  const [error, setError] = useState<string | null>(null);

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: () => api.get<UserResponse[]>("/api/admin/users"),
  });

  const createUser = useMutation({
    mutationFn: (body: CreateUserRequest) => api.post<UserResponse>("/api/admin/users", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setForm({ username: "", password: "", displayName: "", roleCode: "WORKER" });
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    createUser.mutate(form);
  };

  return (
    <div>
      <PageHeader title="사용자" />

      <Card className="mb-4">
        <form onSubmit={onSubmit} className="flex items-end gap-3">
          <div className="w-36">
            <Field label="아이디">
              <Input value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} required />
            </Field>
          </div>
          <div className="w-40">
            <Field label="비밀번호 (8자 이상)">
              <Input
                type="password"
                minLength={8}
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
                required
              />
            </Field>
          </div>
          <div className="flex-1">
            <Field label="이름">
              <Input value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} required />
            </Field>
          </div>
          <div className="w-32">
            <Field label="역할">
              <Select value={form.roleCode} onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
                <option value="WORKER">WORKER</option>
                <option value="ADMIN">ADMIN</option>
              </Select>
            </Field>
          </div>
          <Button type="submit" disabled={createUser.isPending}>
            생성
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>

      <Card>
        {usersQuery.isLoading && <Spinner />}
        {usersQuery.data && (
          <Table>
            <thead>
              <tr>
                <Th>아이디</Th>
                <Th>이름</Th>
                <Th>역할</Th>
                <Th>상태</Th>
                <Th />
              </tr>
            </thead>
            <tbody>
              {usersQuery.data.map((user) => (
                <tr key={user.id}>
                  <Td>{user.username}</Td>
                  <Td>{user.displayName}</Td>
                  <Td>
                    <Badge tone={user.roleCode === "ADMIN" ? "blue" : "gray"}>{user.roleCode}</Badge>
                  </Td>
                  <Td>
                    <Badge tone={user.status === "ACTIVE" ? "green" : "red"}>{user.status}</Badge>
                  </Td>
                  <Td>
                    <Link to={`/users/${user.id}`} className="text-indigo-600 hover:underline">
                      작업장 권한 관리
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
