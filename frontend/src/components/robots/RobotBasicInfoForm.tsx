import { useState, type FormEvent } from "react";
import { Button, Card, ErrorBanner, Field, Input } from "../ui";
import type { RobotResponse, UpdateRobotRequest } from "../../types/api";

interface RobotBasicInfoFormProps {
  robot: RobotResponse;
  onSubmit: (body: UpdateRobotRequest) => void;
  isPending: boolean;
  error: string | null;
}

export function RobotBasicInfoForm({ robot, onSubmit, isPending, error }: RobotBasicInfoFormProps) {
  const [name, setName] = useState(robot.name);
  const [model, setModel] = useState(robot.model ?? "");
  const [manufacturer, setManufacturer] = useState(robot.manufacturer ?? "");

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    onSubmit({ name, model: model || null, manufacturer: manufacturer || null });
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">기본정보 수정</h2>
      <form onSubmit={handleSubmit} className="grid grid-cols-3 gap-3">
        <Field label="이름">
          <Input value={name} onChange={(e) => setName(e.target.value)} required />
        </Field>
        <Field label="모델">
          <Input value={model} onChange={(e) => setModel(e.target.value)} />
        </Field>
        <Field label="제조사">
          <Input value={manufacturer} onChange={(e) => setManufacturer(e.target.value)} />
        </Field>
        <div className="col-span-3">
          <Button type="submit" disabled={isPending}>
            저장
          </Button>
        </div>
      </form>
      <ErrorBanner message={error} />
    </Card>
  );
}
