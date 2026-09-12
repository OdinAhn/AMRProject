import { useEffect, useState, type FormEvent } from "react";
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

  useEffect(() => {
    setCode("");
    setName("");
  }, [resetToken]);

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
