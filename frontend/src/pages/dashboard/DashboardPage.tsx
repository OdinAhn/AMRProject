import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { KpiTiles } from "../../components/dashboard/KpiTiles";
import { LiveMapView } from "../../components/dashboard/LiveMapView";
import { RobotStatusTable } from "../../components/dashboard/RobotStatusTable";
import { Card, ConfirmButton, ErrorBanner, PageHeader, Select, Spinner } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { useToast } from "../../lib/toastContext";
import { useSites } from "../../lib/useSites";
import type { EmergencyStopResponse, SiteOverviewResponse } from "../../types/api";

const SITE_STORAGE_KEY = "amr.dashboard.siteId";

function readStoredSiteId(): string {
  try {
    return localStorage.getItem(SITE_STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
}

/**
 * 30단계: 관리자 첫 화면. 물류센터 하나의 현황(로봇 연결·배터리·현재 작업, 진행/대기/복구 필요 작업 수, 열린 알람)과
 * 활성 지도 위 로봇 위치를 한 화면에 보여준다. 로봇·작업·알람 이벤트(SSE)가 오면 개요를 다시 조회한다.
 */
export function DashboardPage() {
  const sitesQuery = useSites();
  const [selectedSiteId, setSelectedSiteId] = useState(readStoredSiteId);
  const sites = sitesQuery.data ?? [];
  const siteId = sites.some((s) => String(s.id) === selectedSiteId) ? selectedSiteId : sites[0] ? String(sites[0].id) : "";

  const overviewQuery = useQuery({
    queryKey: ["overview", siteId],
    queryFn: () => api.get<SiteOverviewResponse>(`/api/admin/sites/${siteId}/overview`),
    enabled: !!siteId,
    // 실시간 이벤트로 갱신되지만, 연결이 끊겼을 때를 위한 느린 안전장치
    refetchInterval: 30000,
  });

  // 33단계: 물류센터 전체 비상정지
  const queryClient = useQueryClient();
  const { showToast } = useToast();
  const stopAll = useMutation({
    mutationFn: () => api.post<EmergencyStopResponse[]>(`/api/admin/sites/${siteId}/emergency-stop`),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["overview", siteId] });
      showToast(`운영 중인 로봇 ${result.length}대에 비상정지를 요청했습니다.`);
    },
    onError: (err) => showToast(errorMessage(err, "전체 비상정지 요청에 실패했습니다."), "error"),
  });

  const changeSite = (value: string) => {
    setSelectedSiteId(value);
    try {
      localStorage.setItem(SITE_STORAGE_KEY, value);
    } catch {
      // 저장소를 쓸 수 없는 환경이면 선택만 유지한다
    }
  };

  const overview = overviewQuery.data;

  return (
    <div>
      <PageHeader
        title="관제 대시보드"
        actions={
          sites.length > 0 && (
            <div className="flex items-center gap-3">
              <ConfirmButton
                title="이 물류센터의 모든 로봇을 비상정지할까요?"
                message="운영 중인 로봇이 모두 즉시 멈추고, 진행 중인 작업은 복구 필요 상태가 됩니다. 해제는 로봇마다 현장에서 해야 합니다."
                confirmLabel="전체 비상정지"
                onConfirm={() => stopAll.mutate()}
                disabled={!siteId || stopAll.isPending}
              >
                전체 비상정지
              </ConfirmButton>
              <div className="w-56">
                <Select value={siteId} onChange={(e) => changeSite(e.target.value)} aria-label="물류센터 선택">
                  {sites.map((site) => (
                    <option key={site.id} value={site.id}>
                      {site.name} ({site.code})
                    </option>
                  ))}
                </Select>
              </div>
            </div>
          )
        }
      />

      {sitesQuery.data && sites.length === 0 && (
        <Card>
          <p className="text-sm text-gray-600">
            등록된 물류센터가 없습니다.{" "}
            <Link to="/sites" className="text-indigo-600 hover:underline">
              물류센터를 먼저 등록하세요.
            </Link>
          </p>
        </Card>
      )}

      <ErrorBanner message={overviewQuery.error ? errorMessage(overviewQuery.error) : null} />
      {overviewQuery.isLoading && <Spinner />}

      {overview && (
        <>
          <KpiTiles overview={overview} />
          <div className="grid grid-cols-1 gap-4 xl:grid-cols-5">
            <div className="xl:col-span-3">
              {overview.activeMapVersionId ? (
                <LiveMapView mapVersionId={overview.activeMapVersionId} robots={overview.robots} />
              ) : (
                <Card>
                  <h2 className="mb-2 text-sm font-semibold text-gray-700">실시간 지도</h2>
                  <p className="text-sm text-gray-500">
                    이 물류센터에 활성화된 지도가 없습니다.{" "}
                    <Link to="/maps" className="text-indigo-600 hover:underline">
                      지도를 만들고 검증·활성화
                    </Link>
                    한 뒤, 작업장을 지도 노드에 연결하면 로봇 위치가 표시됩니다.
                  </p>
                </Card>
              )}
            </div>
            <div className="xl:col-span-2">
              <RobotStatusTable robots={overview.robots} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
