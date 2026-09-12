import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input, Select } from "../ui";
import type { CreateStationRequest, StationType } from "../../types/api";

const STATION_TYPES: StationType[] = ["PICKUP", "DROPOFF", "WAITING", "CHARGING", "GENERIC"];

interface StationCreateFormProps {
  onSubmit: (body: CreateStationRequest) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

export function StationCreateForm({ onSubmit, isPending, error, resetToken }: StationCreateFormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [stationType, setStationType] = useState<StationType>("GENERIC");

  useEffect(() => {
    setCode("");
    setName("");
  }, [resetToken]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit({ code, name, stationType });
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">작업장 추가</h2>
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
        <div className="w-40">
          <Field label="유형">
            <Select value={stationType} onChange={(e) => setStationType(e.target.value as StationType)}>
              {STATION_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </Select>
          </Field>
        </div>
        <Button type="submit" disabled={isPending}>
          추가
        </Button>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
