import { useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input } from "../ui";
import type { CreateSiteRequest } from "../../types/api";

interface SiteCreateFormProps {
  onSubmit: (body: CreateSiteRequest) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

export function SiteCreateForm({ onSubmit, isPending, error, resetToken }: SiteCreateFormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");

  // 32단계: 부모가 resetToken을 올리면(저장 성공 등) 입력값을 비운다. effect에서 setState하면 렌더가 한 번 더 돌아서,
  // 렌더 중에 이전 값과 비교해 바로 조정한다(React 권장 패턴).
  const [appliedResetToken, setAppliedResetToken] = useState(resetToken);
  if (appliedResetToken !== resetToken) {
    setAppliedResetToken(resetToken);
    setCode("");
    setName("");
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit({ code, name });
  };

  return (
    <Card className="mb-4">
      <form onSubmit={handleSubmit} className="flex items-end gap-3">
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
        <Button type="submit" disabled={isPending}>
          생성
        </Button>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
