import { Link } from "react-router";
import { homePathFor, useAuth } from "../lib/authContext";

export function NotFoundPage() {
  const { user } = useAuth();
  return (
    <div className="flex h-screen flex-col items-center justify-center gap-3 bg-gray-50">
      <p className="text-4xl font-semibold text-gray-300">404</p>
      <p className="text-sm text-gray-600">요청한 화면을 찾을 수 없습니다.</p>
      <Link to={user ? homePathFor(user) : "/login"} className="text-sm font-medium text-indigo-600 hover:underline">
        처음 화면으로
      </Link>
    </div>
  );
}
