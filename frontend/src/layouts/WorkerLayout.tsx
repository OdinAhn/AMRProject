import { Link, Outlet } from "react-router";
import { ConnectionStatusBadge } from "../components/events/ConnectionStatusBadge";
import { useAuth } from "../lib/authContext";
import { EventStreamProvider } from "../lib/eventStream";
import { useEventStream } from "../lib/eventStreamContext";

export function WorkerLayout() {
  return (
    <EventStreamProvider>
      <WorkerShell />
    </EventStreamProvider>
  );
}

function WorkerShell() {
  const { user, logout } = useAuth();
  const { status } = useEventStream();

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="flex items-center justify-between border-b border-gray-200 bg-white px-4 py-3">
        <Link to="/worker/stations" className="text-base font-semibold text-gray-900">
          AMR 작업자 화면
        </Link>
        <div className="flex items-center gap-3 text-sm text-gray-600">
          <ConnectionStatusBadge status={status} />
          <Link to="/worker/account" className="hover:text-gray-900 hover:underline">
            {user?.displayName}
          </Link>
          <button onClick={logout} className="font-medium text-gray-600 hover:text-gray-900">
            로그아웃
          </button>
        </div>
      </header>
      <main className="mx-auto max-w-3xl p-4">
        <Outlet />
      </main>
    </div>
  );
}
