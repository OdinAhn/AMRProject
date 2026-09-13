import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { api, clearToken, getToken, registerUnauthorizedHandler, setToken } from "./apiClient";
import type { LoginResponse, UserResponse } from "../types/api";

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<UserResponse>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** 역할별 로그인 후 랜딩 경로 (12단계: WORKER는 관리자 화면에 접근할 수 없다). */
export function homePathFor(user: UserResponse): string {
  return user.roleCode === "WORKER" ? "/worker/stations" : "/sites";
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const logout = () => {
    clearToken();
    setUser(null);
  };

  useEffect(() => {
    registerUnauthorizedHandler(logout);
  }, []);

  useEffect(() => {
    if (!getToken()) {
      setLoading(false);
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

  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth는 AuthProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
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
