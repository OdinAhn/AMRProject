import { useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input } from "../ui";

interface MapCreateFormProps {
  onSubmit: (name: string) => void;
  isPending: boolean;
  disabled: boolean;
  error: string | null;
  resetToken: number;
}

export function MapCreateForm({ onSubmit, isPending, disabled, error, resetToken }: MapCreateFormProps) {
  const [name, setName] = useState("");

  // 32단계: 부모가 resetToken을 올리면(저장 성공 등) 입력값을 비운다. effect에서 setState하면 렌더가 한 번 더 돌아서,
  // 렌더 중에 이전 값과 비교해 바로 조정한다(React 권장 패턴).
  const [appliedResetToken, setAppliedResetToken] = useState(resetToken);
  if (appliedResetToken !== resetToken) {
    setAppliedResetToken(resetToken);
    setName("");
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit(name);
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">지도 생성</h2>
      <form onSubmit={handleSubmit} className="flex items-end gap-3">
        <div className="flex-1">
          <Field label="이름">
            <Input value={name} onChange={(e) => setName(e.target.value)} required />
          </Field>
        </div>
        <Button type="submit" disabled={isPending || disabled}>
          생성
        </Button>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
