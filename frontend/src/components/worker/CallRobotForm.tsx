import { useEffect, useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Select } from "../ui";
import type { StationResponse } from "../../types/api";

interface CallRobotFormProps {
  stationId: string | undefined;
  destinationOptions: StationResponse[];
  onSubmit: (destinationId: string) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

export function CallRobotForm({ stationId, destinationOptions, onSubmit, isPending, error, resetToken }: CallRobotFormProps) {
  const [destinationId, setDestinationId] = useState("");

  useEffect(() => {
    setDestinationId("");
  }, [resetToken]);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!destinationId) return;
    onSubmit(destinationId);
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">로봇 호출</h2>
      <form className="flex items-end gap-3" onSubmit={handleSubmit}>
        <div className="w-56">
          <Field label="목적지 작업장">
            <Select value={destinationId} onChange={(e) => setDestinationId(e.target.value)} required>
              <option value="">선택</option>
              {destinationOptions
                .filter((s) => String(s.id) !== stationId)
                .map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.code})
                  </option>
                ))}
            </Select>
          </Field>
        </div>
        <Button type="submit" disabled={!destinationId || isPending}>
          로봇 호출
        </Button>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
