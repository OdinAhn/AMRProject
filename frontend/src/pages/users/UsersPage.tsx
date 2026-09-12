import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { UserCreateForm } from "../../components/users/UserCreateForm";
import { UserTable } from "../../components/users/UserTable";
import { PageHeader, Spinner } from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type { CreateUserRequest, UserResponse } from "../../types/api";

export function UsersPage() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [resetToken, setResetToken] = useState(0);

  const usersQuery = useQuery({
    queryKey: ["users"],
    queryFn: () => api.get<UserResponse[]>("/api/admin/users"),
  });

  const createUser = useMutation({
    mutationFn: (body: CreateUserRequest) => api.post<UserResponse>("/api/admin/users", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] });
      setResetToken((t) => t + 1);
      setError(null);
    },
    onError: (err) => setError(err instanceof ApiError ? err.message : "생성에 실패했습니다."),
  });

  return (
    <div>
      <PageHeader title="사용자" />

      <UserCreateForm onSubmit={(body) => createUser.mutate(body)} isPending={createUser.isPending} error={error} resetToken={resetToken} />

      {usersQuery.isLoading && <Spinner />}
      {usersQuery.data && <UserTable users={usersQuery.data} />}
    </div>
  );
}
