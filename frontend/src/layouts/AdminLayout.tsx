import { NavLink, Outlet } from "react-router";
import { useAuth } from "../lib/auth";

const NAV_ITEMS = [
  { to: "/sites", label: "물류센터/작업장" },
  { to: "/robots", label: "로봇" },
  { to: "/maps", label: "지도" },
  { to: "/tasks", label: "작업" },
  { to: "/dispatch", label: "디스패치" },
  { to: "/users", label: "사용자" },
  { to: "/alarms", label: "알람" },
  { to: "/events", label: "실시간 이벤트" },
  { to: "/worker/stations", label: "작업자 화면" },
];

export function AdminLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="flex h-screen bg-gray-50">
      <aside className="flex w-56 flex-shrink-0 flex-col border-r border-gray-200 bg-white">
        <div className="border-b border-gray-200 px-4 py-4">
          <div className="text-sm font-semibold text-gray-900">AMR 관제 시스템</div>
          <div className="text-xs text-gray-500">관리자 화면</div>
        </div>
        <nav className="flex-1 space-y-1 px-2 py-3">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `block rounded-md px-3 py-2 text-sm font-medium ${
                  isActive ? "bg-indigo-50 text-indigo-700" : "text-gray-700 hover:bg-gray-100"
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-gray-200 px-4 py-3">
          <div className="mb-2 text-xs text-gray-500">
            {user?.displayName} ({user?.roleCode})
          </div>
          <button onClick={logout} className="text-sm font-medium text-gray-600 hover:text-gray-900">
            로그아웃
          </button>
        </div>
      </aside>
      <main className="flex-1 overflow-y-auto p-6">
        <Outlet />
      </main>
    </div>
  );
}
