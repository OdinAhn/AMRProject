import { createContext, useContext } from "react";
import type { UserResponse } from "../types/api";

// 32단계: 컨텍스트·훅·유틸을 컴포넌트 파일(auth.tsx)과 분리했다 — 한 파일이 컴포넌트와 그 외 값을 함께 내보내면
// Vite Fast Refresh가 그 파일을 수정할 때 상태를 보존하지 못한다(oxlint react/only-export-components).

export interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<UserResponse>;
  logout: () => void;
  /** 32단계: 비밀번호 변경처럼 새 토큰을 받은 뒤 저장된 토큰과 사용자 정보를 바꾼다. */
  replaceSession: (token: string, user: UserResponse) => void;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** 역할별 로그인 후 랜딩 경로 (12단계: WORKER는 관리자 화면에 접근할 수 없다. 30단계: 관리자는 대시보드). */
export function homePathFor(user: UserResponse): string {
  return user.roleCode === "WORKER" ? "/worker/stations" : "/dashboard";
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth는 AuthProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
}
