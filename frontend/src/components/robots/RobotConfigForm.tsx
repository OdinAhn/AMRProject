import { useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input } from "../ui";
import type { CreateRobotConfigurationRequest } from "../../types/api";

interface RobotConfigFormProps {
  onSubmit: (body: CreateRobotConfigurationRequest) => void;
  isPending: boolean;
  error: string | null;
}

export function RobotConfigForm({ onSubmit, isPending, error }: RobotConfigFormProps) {
  const [form, setForm] = useState({
    maxPayloadKg: "50",
    maxSpeedMps: "1.5",
    batteryCapacityWh: "500",
    chargingLowThresholdPct: "20",
    chargingTargetPct: "90",
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit({
      maxPayloadKg: Number(form.maxPayloadKg),
      maxSpeedMps: Number(form.maxSpeedMps),
      batteryCapacityWh: Number(form.batteryCapacityWh),
      chargingLowThresholdPct: Number(form.chargingLowThresholdPct),
      chargingTargetPct: Number(form.chargingTargetPct),
    });
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">새 설정 생성</h2>
      <form onSubmit={handleSubmit} className="grid grid-cols-5 gap-3">
        <Field label="최대 적재(kg)">
          <Input
            type="number"
            step="0.1"
            value={form.maxPayloadKg}
            onChange={(e) => setForm({ ...form, maxPayloadKg: e.target.value })}
            required
          />
        </Field>
        <Field label="최대 속도(m/s)">
          <Input
            type="number"
            step="0.1"
            value={form.maxSpeedMps}
            onChange={(e) => setForm({ ...form, maxSpeedMps: e.target.value })}
            required
          />
        </Field>
        <Field label="배터리 용량(Wh)">
          <Input
            type="number"
            step="0.1"
            value={form.batteryCapacityWh}
            onChange={(e) => setForm({ ...form, batteryCapacityWh: e.target.value })}
            required
          />
        </Field>
        <Field label="저배터리 임계값(%)">
          <Input
            type="number"
            value={form.chargingLowThresholdPct}
            onChange={(e) => setForm({ ...form, chargingLowThresholdPct: e.target.value })}
            required
          />
        </Field>
        <Field label="충전 목표(%)">
          <Input
            type="number"
            value={form.chargingTargetPct}
            onChange={(e) => setForm({ ...form, chargingTargetPct: e.target.value })}
            required
          />
        </Field>
        <div className="col-span-5">
          <Button type="submit" disabled={isPending}>
            설정 생성 및 적용
          </Button>
        </div>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
