import { Button, Select } from "../ui";
import type { SiteResponse } from "../../types/api";

interface DispatchFormProps {
  sites: SiteResponse[];
  siteId: string;
  onSiteIdChange: (value: string) => void;
  onRun: () => void;
  isPending: boolean;
}

export function DispatchForm({ sites, siteId, onSiteIdChange, onRun, isPending }: DispatchFormProps) {
  return (
    <div className="flex items-end gap-3">
      <div className="w-56">
        <Select value={siteId} onChange={(e) => onSiteIdChange(e.target.value)}>
          <option value="">물류센터 선택</option>
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.name} ({site.code})
            </option>
          ))}
        </Select>
      </div>
      <Button onClick={onRun} disabled={!siteId || isPending}>
        디스패치 실행
      </Button>
    </div>
  );
}
