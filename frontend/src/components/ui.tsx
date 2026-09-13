import { useState } from "react";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

export function PageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="mb-4 flex items-center justify-between">
      <h1 className="text-xl font-semibold text-gray-900">{title}</h1>
      {actions}
    </div>
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-lg border border-gray-200 bg-white p-4 shadow-sm ${className}`}>{children}</div>;
}

export function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "danger" }) {
  const base = "rounded-md px-3 py-1.5 text-sm font-medium disabled:opacity-50 disabled:cursor-not-allowed";
  const variants = {
    primary: "bg-indigo-600 text-white hover:bg-indigo-700",
    secondary: "bg-white text-gray-700 border border-gray-300 hover:bg-gray-50",
    danger: "bg-red-600 text-white hover:bg-red-700",
  };
  return <button className={`${base} ${variants[variant]} ${className}`} {...props} />;
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none ${props.className ?? ""}`}
    />
  );
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={`w-full rounded-md border border-gray-300 px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none ${props.className ?? ""}`}
    />
  );
}

export function Label({ children }: { children: ReactNode }) {
  return <label className="mb-1 block text-sm font-medium text-gray-700">{children}</label>;
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Label>{label}</Label>
      {children}
    </div>
  );
}

export function ErrorBanner({ message }: { message: string | null | undefined }) {
  if (!message) return null;
  return <div className="mb-3 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{message}</div>;
}

export function Spinner() {
  return <div className="py-8 text-center text-sm text-gray-500">불러오는 중...</div>;
}

export function Badge({ children, tone = "gray" }: { children: ReactNode; tone?: "gray" | "green" | "red" | "yellow" | "blue" }) {
  const tones: Record<string, string> = {
    gray: "bg-gray-100 text-gray-700",
    green: "bg-green-100 text-green-700",
    red: "bg-red-100 text-red-700",
    yellow: "bg-yellow-100 text-yellow-800",
    blue: "bg-blue-100 text-blue-700",
  };
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${tones[tone]}`}>{children}</span>;
}

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">{children}</table>
    </div>
  );
}

export function Th({ children }: { children?: ReactNode }) {
  return <th className="border-b border-gray-200 pb-2 pr-4 font-medium text-gray-500">{children}</th>;
}

export function Td({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <td className={`border-b border-gray-100 py-2 pr-4 ${className}`}>{children}</td>;
}

/**
 * 28단계: 되돌릴 수 없는 동작(작업 취소·최종 실패 처리·로봇 운영 상태 변경 등)을 한 번 더 확인받는 대화상자.
 * open이 false면 아무것도 그리지 않는다 — 부모가 open 상태를 들고 있다.
 */
export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = "확인",
  variant = "danger",
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  variant?: "primary" | "danger";
  onConfirm: () => void;
  onCancel: () => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4" onClick={onCancel}>
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        className="w-full max-w-sm rounded-lg bg-white p-5 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 id="confirm-dialog-title" className="text-base font-semibold text-gray-900">
          {title}
        </h2>
        {message && <div className="mt-2 text-sm text-gray-600">{message}</div>}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} autoFocus>
            아니요
          </Button>
          <Button variant={variant} onClick={onConfirm}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}

/** 누르면 ConfirmDialog를 먼저 띄우고, 확인했을 때만 onConfirm을 부르는 버튼. */
export function ConfirmButton({
  title,
  message,
  confirmLabel,
  onConfirm,
  variant = "danger",
  className = "",
  disabled,
  children,
}: {
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  onConfirm: () => void;
  variant?: "primary" | "secondary" | "danger";
  className?: string;
  disabled?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button variant={variant} className={className} disabled={disabled} onClick={() => setOpen(true)}>
        {children}
      </Button>
      <ConfirmDialog
        open={open}
        title={title}
        message={message}
        confirmLabel={confirmLabel}
        variant={variant === "danger" ? "danger" : "primary"}
        onCancel={() => setOpen(false)}
        onConfirm={() => {
          setOpen(false);
          onConfirm();
        }}
      />
    </>
  );
}

/** 31단계: 목록 아래 페이지 이동. 한 페이지뿐이면 전체 건수만 보여준다. */
export function Pagination({
  page,
  onPageChange,
}: {
  page: { page: number; totalPages: number; totalElements: number };
  onPageChange: (page: number) => void;
}) {
  return (
    <div className="mt-3 flex items-center justify-between text-sm text-gray-600">
      <span>전체 {page.totalElements.toLocaleString()}건</span>
      {page.totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="secondary" disabled={page.page <= 0} onClick={() => onPageChange(page.page - 1)}>
            이전
          </Button>
          <span className="tabular-nums">
            {page.page + 1} / {page.totalPages}
          </span>
          <Button variant="secondary" disabled={page.page >= page.totalPages - 1} onClick={() => onPageChange(page.page + 1)}>
            다음
          </Button>
        </div>
      )}
    </div>
  );
}
