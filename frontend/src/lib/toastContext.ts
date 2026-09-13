import { createContext, useContext } from "react";

// 32단계: 훅·컨텍스트를 ToastProvider 컴포넌트 파일과 분리했다(Fast Refresh, oxlint react/only-export-components).

export type ToastTone = "success" | "error";

export interface ToastContextValue {
  showToast: (message: string, tone?: ToastTone) => void;
}

export const ToastContext = createContext<ToastContextValue | undefined>(undefined);

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast는 ToastProvider 안에서만 사용할 수 있습니다.");
  }
  return context;
}
