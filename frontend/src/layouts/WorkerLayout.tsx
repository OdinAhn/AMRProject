import { Link, Outlet } from "react-router";
import { useAuth } from "../lib/auth";

export function WorkerLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3">
        <Link to="/worker/stations" className="text-sm font-semibold text-gray-900">
          AMR 작업자 화면
        </Link>
        <div className="flex items-center gap-3 text-sm text-gray-600">
          <span>{user?.displayName}</span>
          <button onClick={logout} className="font-medium text-gray-600 hover:text-gray-900">
            로그아웃
          </button>
        </div>
      </header>
      <main className="p-4">
        <Outlet />
      </main>
    </div>
  );
}
