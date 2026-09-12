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

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="flex h-screen items-center justify-center text-gray-500">로딩 중...</div>;
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return <>{children}</>;
}
