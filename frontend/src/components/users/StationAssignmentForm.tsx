import { useEffect, useState } from "react";
import { Button, Card, ErrorBanner, Select } from "../ui";
import type { SiteResponse, StationResponse } from "../../types/api";

interface StationAssignmentFormProps {
  sites: SiteResponse[];
  stations: StationResponse[];
  onSubmit: (stationId: number) => void;
  isPending: boolean;
  error: string | null;
  resetToken: number;
  onSiteChange: (siteId: string) => void;
}

export function StationAssignmentForm({ sites, stations, onSubmit, isPending, error, resetToken, onSiteChange }: StationAssignmentFormProps) {
  const [siteId, setSiteId] = useState("");
  const [stationId, setStationId] = useState("");

  useEffect(() => {
    setStationId("");
  }, [resetToken]);

  const handleSiteChange = (value: string) => {
    setSiteId(value);
    setStationId("");
    onSiteChange(value);
  };

  return (
    <Card className="mb-4">
      <h2 className="mb-3 text-sm font-semibold text-gray-700">권한 부여</h2>
      <div className="flex items-end gap-3">
        <div className="w-48">
          <Select value={siteId} onChange={(e) => handleSiteChange(e.target.value)}>
            <option value="">물류센터 선택</option>
            {sites.map((site) => (
              <option key={site.id} value={site.id}>
                {site.name}
              </option>
            ))}
          </Select>
        </div>
        <div className="w-48">
          <Select value={stationId} onChange={(e) => setStationId(e.target.value)} disabled={!siteId}>
            <option value="">작업장 선택</option>
            {stations.map((station) => (
              <option key={station.id} value={station.id}>
                {station.name} ({station.code})
              </option>
            ))}
          </Select>
        </div>
        <Button onClick={() => onSubmit(Number(stationId))} disabled={!stationId || isPending}>
          부여
        </Button>
      </div>
      <ErrorBanner message={error} />
    </Card>
  );
}
