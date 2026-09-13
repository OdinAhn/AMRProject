import { useState, type FormEvent } from "react";
import { PRIORITY_OPTIONS } from "../../lib/labels";
import { Button, Card, ErrorBanner, Field, Select } from "../ui";
import type { StationResponse } from "../../types/api";

interface CallRobotFormProps {
  stationId: string | undefined;
  destinationOptions: StationResponse[];
  onSubmit: (destinationId: string, priority: number) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
}

export function CallRobotForm({ stationId, destinationOptions, onSubmit, isPending, error, resetToken }: CallRobotFormProps) {
  const [destinationId, setDestinationId] = useState("");
  const [priority, setPriority] = useState(0);

  // 32단계: 부모가 resetToken을 올리면(저장 성공 등) 입력값을 비운다. effect에서 setState하면 렌더가 한 번 더 돌아서,
  // 렌더 중에 이전 값과 비교해 바로 조정한다(React 권장 패턴).
  const [appliedResetToken, setAppliedResetToken] = useState(resetToken);
  if (appliedResetToken !== resetToken) {
    setAppliedResetToken(resetToken);
    setDestinationId("");
    setPriority(0);
  }

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!destinationId) return;
    onSubmit(destinationId, priority);
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">로봇 호출</h2>
      <form className="flex flex-wrap items-end gap-3" onSubmit={handleSubmit}>
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
        {/* 33단계: 급한 운반은 긴급으로 호출하면 자동 배정이 먼저 처리한다 */}
        <div className="w-28">
          <Field label="우선순위">
            <Select value={priority} onChange={(e) => setPriority(Number(e.target.value))}>
              {PRIORITY_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
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
