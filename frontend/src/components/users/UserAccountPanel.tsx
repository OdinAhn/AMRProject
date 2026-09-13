import { useState, type FormEvent } from "react";
import { labels } from "../../lib/labels";
import { Badge, Button, Card, ConfirmDialog, ErrorBanner, Field, Input, Select } from "../ui";
import type { UpdateUserRequest, UserResponse, UserStatus } from "../../types/api";

const ROLE_CODES = ["ADMIN", "WORKER"];
const USER_STATUSES: UserStatus[] = ["ACTIVE", "DISABLED"];

interface UserAccountPanelProps {
  user: UserResponse;
  onUpdate: (body: UpdateUserRequest) => void;
  isUpdating: boolean;
  onResetPassword: (newPassword: string) => void;
  isResettingPassword: boolean;
  error: string | null;
}

type PendingChange = { kind: "role"; value: string } | { kind: "status"; value: UserStatus } | { kind: "password"; value: string };

/**
 * 32단계: 사용자 계정 관리 — 역할·상태 변경과 비밀번호 초기화. 모두 되돌리기 어렵거나 그 사용자의 로그인을 끊는 동작이라
 * 확인창을 거친다. 마지막 활성 관리자를 강등·비활성화하는 요청은 서버가 409로 막는다.
 */
export function UserAccountPanel({ user, onUpdate, isUpdating, onResetPassword, isResettingPassword, error }: UserAccountPanelProps) {
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [pending, setPending] = useState<PendingChange | null>(null);
  const passwordMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword;

  const handlePasswordSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (newPassword.length < 8 || passwordMismatch) return;
    setPending({ kind: "password", value: newPassword });
  };

  const confirm = () => {
    if (!pending) return;
    if (pending.kind === "role") onUpdate({ roleCode: pending.value });
    if (pending.kind === "status") onUpdate({ status: pending.value });
    if (pending.kind === "password") {
      onResetPassword(pending.value);
      setNewPassword("");
      setConfirmPassword("");
    }
    setPending(null);
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">계정</h2>
      <div className="mb-4 flex flex-wrap items-center gap-3 text-sm">
        <span className="font-medium text-gray-900">{user.displayName}</span>
        <span className="font-mono text-gray-500">{user.username}</span>
        <Badge tone={user.roleCode === "ADMIN" ? "blue" : "gray"}>{labels.role(user.roleCode)}</Badge>
        <Badge tone={user.status === "ACTIVE" ? "green" : "red"}>{labels.userStatus(user.status)}</Badge>
      </div>

      <div className="flex flex-wrap items-end gap-3 border-b border-gray-100 pb-4">
        <div className="w-40">
          <Field label="역할">
            <Select value={user.roleCode} disabled={isUpdating} onChange={(e) => setPending({ kind: "role", value: e.target.value })}>
              {ROLE_CODES.map((code) => (
                <option key={code} value={code}>
                  {labels.role(code)}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <div className="w-40">
          <Field label="상태">
            <Select
              value={user.status}
              disabled={isUpdating}
              onChange={(e) => setPending({ kind: "status", value: e.target.value as UserStatus })}
            >
              {USER_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {labels.userStatus(status)}
                </option>
              ))}
            </Select>
          </Field>
        </div>
      </div>

      <form onSubmit={handlePasswordSubmit} className="mt-4 flex flex-wrap items-end gap-3">
        <div className="w-48">
          <Field label="새 비밀번호 (8자 이상)">
            <Input type="password" autoComplete="new-password" minLength={8} value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
          </Field>
        </div>
        <div className="w-48">
          <Field label="새 비밀번호 확인">
            <Input type="password" autoComplete="new-password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
          </Field>
        </div>
        <Button type="submit" variant="secondary" disabled={isResettingPassword || newPassword.length < 8 || newPassword !== confirmPassword}>
          비밀번호 초기화
        </Button>
        {passwordMismatch && <p className="w-full text-xs text-red-600">두 비밀번호가 일치하지 않습니다.</p>}
      </form>

      <ErrorBanner message={error} />

      <ConfirmDialog
        open={pending !== null}
        title={
          pending?.kind === "role"
            ? `역할을 '${labels.role(pending.value)}'(으)로 바꿀까요?`
            : pending?.kind === "status"
              ? `상태를 '${labels.userStatus(pending.value)}'(으)로 바꿀까요?`
              : "비밀번호를 초기화할까요?"
        }
        message={
          pending?.kind === "status" && pending.value === "DISABLED"
            ? "비활성화하면 이 사용자는 즉시 로그아웃되고 다시 로그인할 수 없습니다."
            : pending?.kind === "password"
              ? "이 사용자가 로그인해 있던 모든 기기에서 로그아웃됩니다. 새 비밀번호를 사용자에게 안전하게 전달하세요."
              : "권한이 즉시 바뀝니다."
        }
        confirmLabel="변경"
        variant={pending?.kind === "role" ? "primary" : "danger"}
        onCancel={() => setPending(null)}
        onConfirm={confirm}
      />
    </Card>
  );
}
