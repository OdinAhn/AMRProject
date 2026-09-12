import { useState, type FormEvent } from "react";
import { Button, ErrorBanner, Field, Input } from "../ui";
import { ApiError } from "../../lib/apiClient";

interface LoginFormProps {
  onSubmit: (username: string, password: string) => Promise<void>;
}

export function LoginForm({ onSubmit }: LoginFormProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(username, password);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "로그인에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="w-80 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <h1 className="mb-1 text-lg font-semibold text-gray-900">AMR 관제 시스템</h1>
      <p className="mb-4 text-sm text-gray-500">로그인</p>
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
  );
}
