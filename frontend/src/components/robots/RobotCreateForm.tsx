import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input, Select } from "../ui";
import type { SiteResponse } from "../../types/api";

interface RobotCreateFormProps {
  sites: SiteResponse[];
  onSubmit: (body: { code: string; name: string; siteId: number }) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

export function RobotCreateForm({ sites, onSubmit, isPending, error, resetToken }: RobotCreateFormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [siteId, setSiteId] = useState<string>("");
  const [localError, setLocalError] = useState<string | null>(null);

  useEffect(() => {
    setCode("");
    setName("");
  }, [resetToken]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!siteId) {
      setLocalError("물류센터를 선택하세요.");
      return;
    }
    setLocalError(null);
    onSubmit({ code, name, siteId: Number(siteId) });
  };

  return (
    <Card className="mb-4">
      <form onSubmit={handleSubmit} className="flex items-end gap-3">
        <div className="w-32">
          <Field label="코드">
            <Input value={code} onChange={(e) => setCode(e.target.value)} required />
          </Field>
        </div>
        <div className="flex-1">
          <Field label="이름">
            <Input value={name} onChange={(e) => setName(e.target.value)} required />
          </Field>
        </div>
        <div className="w-48">
          <Field label="물류센터">
            <Select value={siteId} onChange={(e) => setSiteId(e.target.value)} required>
              <option value="">선택</option>
              {sites.map((site) => (
                <option key={site.id} value={site.id}>
                  {site.name} ({site.code})
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Button type="submit" disabled={isPending}>
          등록
        </Button>
      </form>
      <ErrorBanner message={localError ?? error} />
    </Card>
  );
}
