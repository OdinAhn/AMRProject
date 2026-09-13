import { labels } from "../../lib/labels";
import { Card, Select } from "../ui";
import type { AlarmSeverity, AlarmSourceType, AlarmStatus } from "../../types/api";

const STATUSES: AlarmStatus[] = ["OPEN", "ACKNOWLEDGED", "RESOLVED"];
const SEVERITIES: AlarmSeverity[] = ["INFO", "WARNING", "CRITICAL"];
const SOURCE_TYPES: AlarmSourceType[] = ["ROBOT", "TASK", "STATION", "SYSTEM"];

interface AlarmFiltersProps {
  status: string;
  severity: string;
  sourceType: string;
  onStatusChange: (value: string) => void;
  onSeverityChange: (value: string) => void;
  onSourceTypeChange: (value: string) => void;
}

export function AlarmFilters({
  status,
  severity,
  sourceType,
  onStatusChange,
  onSeverityChange,
  onSourceTypeChange,
}: AlarmFiltersProps) {
  return (
    <Card className="mb-4">
      <div className="flex flex-wrap gap-4">
        <div className="w-48">
          <Select value={status} onChange={(e) => onStatusChange(e.target.value)}>
            <option value="">전체 상태</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {labels.alarmStatus(s)}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-48">
          <Select value={severity} onChange={(e) => onSeverityChange(e.target.value)}>
            <option value="">전체 심각도</option>
            {SEVERITIES.map((s) => (
              <option key={s} value={s}>
                {labels.alarmSeverity(s)}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-48">
          <Select value={sourceType} onChange={(e) => onSourceTypeChange(e.target.value)}>
            <option value="">전체 대상</option>
            {SOURCE_TYPES.map((s) => (
              <option key={s} value={s}>
                {labels.alarmSourceType(s)}
              </option>
            ))}
          </Select>
        </div>
      </div>
    </Card>
  );
}
