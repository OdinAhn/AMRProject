import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { NavLink, Outlet } from "react-router";
import { ConnectionStatusBadge } from "../components/events/ConnectionStatusBadge";
import { api } from "../lib/apiClient";
import { useAuth } from "../lib/authContext";
import { EventStreamProvider } from "../lib/eventStream";
import { useEventStream } from "../lib/eventStreamContext";
import type { AlarmResponse, PageResponse } from "../types/api";

const NAV_ITEMS = [
  { to: "/dashboard", label: "대시보드" },
  { to: "/statistics", label: "운영 통계" },
  { to: "/sites", label: "물류센터/작업장" },
  { to: "/robots", label: "로봇" },
  { to: "/maps", label: "지도" },
  { to: "/tasks", label: "작업" },
  { to: "/dispatch", label: "디스패치" },
  { to: "/users", label: "사용자" },
  { to: "/alarms", label: "알람", showOpenAlarmCount: true },
  { to: "/events", label: "실시간 이벤트" },
  { to: "/audit-logs", label: "감사 로그" },
  { to: "/worker/stations", label: "작업자 화면" },
];

export function AdminLayout() {
  return (
    <EventStreamProvider>
      <AdminShell />
    </EventStreamProvider>
  );
}

function AdminShell() {
  const { user, logout } = useAuth();
  const { status } = useEventStream();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // 28단계: 사이드바에 미확인(OPEN) 알람 수를 표시한다. 알람 이벤트가 오면 ["alarms"] 쿼리가 무효화돼 즉시 갱신된다.
  // 31단계: 목록 전체를 받아 세던 것을 페이지 응답의 totalElements만 쓰도록 바꿨다(size=1).
  const openAlarmsQuery = useQuery({
    queryKey: ["alarms", "open-count"],
    queryFn: () => api.get<PageResponse<AlarmResponse>>("/api/admin/alarms?status=OPEN&size=1"),
  });
  const openAlarmCount = openAlarmsQuery.data?.totalElements ?? 0;

  return (
    <div className="flex h-screen bg-gray-50">
      {sidebarOpen && <div className="fixed inset-0 z-20 bg-black/30 md:hidden" onClick={() => setSidebarOpen(false)} />}
      <aside
        className={`fixed inset-y-0 left-0 z-30 flex w-56 flex-shrink-0 flex-col border-r border-gray-200 bg-white transition-transform md:static md:translate-x-0 ${
          sidebarOpen ? "translate-x-0" : "-translate-x-full"
        }`}
      >
        <div className="border-b border-gray-200 px-4 py-4">
          <div className="text-sm font-semibold text-gray-900">AMR 관제 시스템</div>
          <div className="text-xs text-gray-500">관리자 화면</div>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-2 py-3">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) =>
                `flex items-center justify-between rounded-md px-3 py-2 text-sm font-medium ${
                  isActive ? "bg-indigo-50 text-indigo-700" : "text-gray-700 hover:bg-gray-100"
                }`
              }
            >
              <span>{item.label}</span>
              {item.showOpenAlarmCount && openAlarmCount > 0 && (
                <span className="rounded-full bg-red-600 px-2 py-0.5 text-xs font-semibold text-white" aria-label={`미확인 알람 ${openAlarmCount}건`}>
                  {openAlarmCount}
                </span>
              )}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-gray-200 px-4 py-3">
          <div className="mb-2 flex items-center gap-2 text-xs text-gray-500">
            실시간 <ConnectionStatusBadge status={status} />
          </div>
          <div className="mb-2 text-xs text-gray-500">
            <NavLink to="/account" className="hover:text-gray-900 hover:underline" onClick={() => setSidebarOpen(false)}>
              {user?.displayName} ({user?.roleCode}) · 내 계정
            </NavLink>
          </div>
          <button onClick={logout} className="text-sm font-medium text-gray-600 hover:text-gray-900">
            로그아웃
          </button>
        </div>
      </aside>
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center gap-3 border-b border-gray-200 bg-white px-4 py-3 md:hidden">
          <button
            onClick={() => setSidebarOpen(true)}
            className="rounded-md border border-gray-300 px-2 py-1 text-sm text-gray-700"
            aria-label="메뉴 열기"
          >
            ☰
          </button>
          <span className="text-sm font-semibold text-gray-900">AMR 관제 시스템</span>
          {openAlarmCount > 0 && (
            <span className="ml-auto rounded-full bg-red-600 px-2 py-0.5 text-xs font-semibold text-white">알람 {openAlarmCount}</span>
          )}
        </header>
        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
