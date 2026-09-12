import { Badge, Button, Card, ErrorBanner } from "../ui";
import type { MapVersionResponse, MapVersionValidationResponse } from "../../types/api";

interface MapVersionHeaderCardProps {
  version: MapVersionResponse;
  isDraft: boolean;
  onValidate: () => void;
  isValidating: boolean;
  onActivate: () => void;
  isActivating: boolean;
  validation: MapVersionValidationResponse | null;
  error: string | null;
}

export function MapVersionHeaderCard({
  version,
  isDraft,
  onValidate,
  isValidating,
  onActivate,
  isActivating,
  validation,
  error,
}: MapVersionHeaderCardProps) {
  return (
    <Card className="mb-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <span className="text-sm text-gray-500">상태</span>
          <Badge tone={version.status === "ACTIVE" ? "green" : "yellow"}>{version.status}</Badge>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={onValidate} disabled={!isDraft || isValidating}>
            검증
          </Button>
          <Button onClick={onActivate} disabled={version.status !== "VALIDATED" || isActivating}>
            활성화
          </Button>
        </div>
      </div>
      {validation && (
        <div className="mt-3">
          {validation.valid ? (
            <p className="text-sm text-green-700">검증 통과 — 활성화할 수 있습니다.</p>
          ) : (
            <ul className="list-disc pl-5 text-sm text-red-700">
              {validation.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      <ErrorBanner message={error} />
    </Card>
  );
}
