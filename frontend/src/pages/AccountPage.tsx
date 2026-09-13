import { useMutation } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input, PageHeader } from "../components/ui";
import { api, errorMessage } from "../lib/apiClient";
import { useAuth } from "../lib/authContext";
import { labels } from "../lib/labels";
import { useToast } from "../lib/toastContext";
import type { ChangePasswordRequest, LoginResponse } from "../types/api";

/**
 * 32단계: 내 계정 — 본인 비밀번호 변경. 바꾸면 다른 기기의 기존 토큰은 모두 무효가 되고, 서버가 돌려준 새 토큰으로
 * 이 화면은 로그인 상태를 유지한다. 관리자·작업자 레이아웃 양쪽에서 같은 화면을 쓴다.
 */
export function AccountPage() {
  const { user, replaceSession } = useAuth();
  const { showToast } = useToast();
  const [form, setForm] = useState({ currentPassword: "", newPassword: "", confirmPassword: "" });
  const [error, setError] = useState<string | null>(null);
  const mismatch = form.confirmPassword.length > 0 && form.newPassword !== form.confirmPassword;

  const changePassword = useMutation({
    mutationFn: (body: ChangePasswordRequest) => api.put<LoginResponse>("/api/auth/password", body),
    onSuccess: (result) => {
      replaceSession(result.token, result.user);
      setForm({ currentPassword: "", newPassword: "", confirmPassword: "" });
      setError(null);
      showToast("비밀번호를 변경했습니다. 다른 기기에서는 다시 로그인해야 합니다.");
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (mismatch) return;
    changePassword.mutate({ currentPassword: form.currentPassword, newPassword: form.newPassword });
  };

  return (
    <div className="max-w-md">
      <PageHeader title="내 계정" />
      {user && (
        <Card className="mb-4">
          <p className="text-sm text-gray-900">
            {user.displayName} <span className="font-mono text-gray-500">({user.username})</span>
          </p>
          <p className="mt-1 text-xs text-gray-500">{labels.role(user.roleCode)}</p>
        </Card>
      )}
      <Card>
        <h2 className="mb-3 text-sm font-semibold text-gray-700">비밀번호 변경</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <Field label="현재 비밀번호">
            <Input
              type="password"
              autoComplete="current-password"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
              required
            />
          </Field>
          <Field label="새 비밀번호 (8자 이상)">
            <Input
              type="password"
              autoComplete="new-password"
              minLength={8}
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
              required
            />
          </Field>
          <Field label="새 비밀번호 확인">
            <Input
              type="password"
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
              required
            />
          </Field>
          {mismatch && <p className="text-xs text-red-600">두 비밀번호가 일치하지 않습니다.</p>}
          <Button type="submit" disabled={changePassword.isPending || mismatch}>
            변경
          </Button>
        </form>
        <ErrorBanner message={error} />
      </Card>
    </div>
  );
}
