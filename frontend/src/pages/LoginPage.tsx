import { useState, type FormEvent } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { Button, ErrorBanner, Field, Input } from "../components/ui";
import { ApiError } from "../lib/apiClient";
import { useAuth } from "../lib/auth";

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    const from = (location.state as { from?: Location })?.from?.pathname ?? "/sites";
    return <Navigate to={from} replace />;
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      navigate("/sites", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "로그인에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50">
      <form onSubmit={onSubmit} className="w-80 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="mb-1 text-lg font-semibold text-gray-900">AMR 관제 시스템</h1>
        <p className="mb-4 text-sm text-gray-500">관리자 로그인</p>
        <ErrorBanner message={error} />
        <div className="space-y-3">
          <Field label="아이디">
            <Input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus required />
          </Field>
          <Field label="비밀번호">
            <Input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </Field>
        </div>
        <Button type="submit" disabled={submitting} className="mt-4 w-full">
          {submitting ? "로그인 중..." : "로그인"}
        </Button>
      </form>
    </div>
  );
}
