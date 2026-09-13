import { useEffect, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { api, clearToken, getToken, registerUnauthorizedHandler, setToken } from "./apiClient";
import { AuthContext, homePathFor, useAuth } from "./authContext";
import type { LoginResponse, UserResponse } from "../types/api";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  // 저장된 토큰이 있을 때만 /me 확인이 끝날 때까지 로딩으로 둔다(32단계: effect 안에서 바로 setState하지 않도록 초기값으로 계산).
  const [loading, setLoading] = useState(() => !!getToken());

  const logout = () => {
    clearToken();
    setUser(null);
  };

  useEffect(() => {
    registerUnauthorizedHandler(logout);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      return;
    }
    api
      .get<UserResponse>("/api/auth/me")
      .then(setUser)
      .catch(() => clearToken())
      .finally(() => setLoading(false));
  }, []);

  const login = async (username: string, password: string) => {
    const result = await api.post<LoginResponse>("/api/auth/login", { username, password });
    setToken(result.token);
    setUser(result.user);
    return result.user;
  };

  const replaceSession = (token: string, nextUser: UserResponse) => {
    setToken(token);
    setUser(nextUser);
  };

  return <AuthContext.Provider value={{ user, loading, login, logout, replaceSession }}>{children}</AuthContext.Provider>;
}

/**
 * 로그인 여부를 확인하고, roles가 주어지면 역할도 확인한다(28단계).
 * 역할이 맞지 않으면 403 화면 대신 그 사용자의 기본 화면으로 보낸다 — 예: WORKER가 주소창에
 * /robots를 입력하면 /worker/stations로 이동한다(이전엔 관리자 레이아웃이 뜨고 API만 403이었다).
 */
export function ProtectedRoute({ children, roles }: { children: ReactNode; roles?: string[] }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-gray-500">로딩 중...</div>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  if (roles && !roles.includes(user.roleCode)) {
    return <Navigate to={homePathFor(user)} replace />;
  }
  return <>{children}</>;
}
