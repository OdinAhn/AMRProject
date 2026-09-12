import { useEffect, useState, type FormEvent } from "react";
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

  useEffect(() => {
    setName("");
  }, [resetToken]);

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
