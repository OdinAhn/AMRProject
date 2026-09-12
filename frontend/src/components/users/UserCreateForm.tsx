import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input, Select } from "../ui";
import type { CreateUserRequest } from "../../types/api";

interface UserCreateFormProps {
  onSubmit: (body: CreateUserRequest) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

const INITIAL_FORM = { username: "", password: "", displayName: "", roleCode: "WORKER" };

export function UserCreateForm({ onSubmit, isPending, error, resetToken }: UserCreateFormProps) {
  const [form, setForm] = useState(INITIAL_FORM);

  useEffect(() => {
    setForm(INITIAL_FORM);
  }, [resetToken]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit(form);
  };

  return (
    <Card className="mb-4">
      <form onSubmit={handleSubmit} className="flex items-end gap-3">
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
        <Button type="submit" disabled={isPending}>
          생성
        </Button>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
