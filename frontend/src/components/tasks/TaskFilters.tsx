import { labels } from "../../lib/labels";
import { Card, Select } from "../ui";
import type { SiteResponse, TaskStatus } from "../../types/api";

const STATUSES: TaskStatus[] = [
  "QUEUED",
  "ASSIGNED",
  "RUNNING",
  "PAUSED",
  "CANCEL_REQUESTED",
  "CANCELED",
  "RECOVERY_REQUIRED",
  "SUCCEEDED",
  "FAILED",
];

interface TaskFiltersProps {
  sites: SiteResponse[];
  siteId: string;
  status: string;
  onSiteIdChange: (value: string) => void;
  onStatusChange: (value: string) => void;
}

export function TaskFilters({ sites, siteId, status, onSiteIdChange, onStatusChange }: TaskFiltersProps) {
  return (
    <Card className="mb-4">
      <div className="flex gap-4">
        <div className="w-56">
          <Select value={siteId} onChange={(e) => onSiteIdChange(e.target.value)}>
            <option value="">전체 물류센터</option>
            {sites.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-56">
          <Select value={status} onChange={(e) => onStatusChange(e.target.value)}>
            <option value="">전체 상태</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {labels.taskStatus(s)}
              </option>
            ))}
          </Select>
        </div>
      </div>
    </Card>
  );
}
