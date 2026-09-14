import type { ErrorResponse } from "../types/api";

const TOKEN_STORAGE_KEY = "amr.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY);
}

export class ApiError extends Error {
  status: number;
  code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

/** 401을 받으면 이 콜백으로 알린다 (auth 컨텍스트가 로그아웃 처리를 등록한다). */
let onUnauthorized: (() => void) | null = null;
export function registerUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  extraHeaders?: Record<string, string>,
): Promise<T> {
  const headers: Record<string, string> = { ...extraHeaders };
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 401) {
    // 36단계: 로그인 실패(INVALID_CREDENTIALS)처럼 서버가 공통 오류 본문을 준 경우엔 그 메시지를 쓴다.
    // 토큰 만료 등 Spring Security의 401은 본문에 code가 없어 기존 문구로 보여준다.
    const error = parseJson(await response.text()) as ErrorResponse | undefined;
    onUnauthorized?.();
    throw new ApiError(401, error?.code ?? "UNAUTHORIZED", error?.code && error.message ? error.message : "인증이 필요합니다.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  const data = parseJson(text);

  if (!response.ok) {
    const error = data as ErrorResponse | undefined;
    // 28단계: Spring Security의 역할 거부(403)는 본문이 공통 오류 포맷(JSON)이 아니다 — 전용 메시지로 보여준다.
    // 작업장 권한 거부(403 STATION_ACCESS_DENIED, 26단계)는 본문에 메시지가 있어 그대로 쓴다.
    const fallbackMessage = response.status === 403 ? "이 작업을 수행할 권한이 없습니다." : "요청이 실패했습니다.";
    throw new ApiError(response.status, error?.code ?? `HTTP_${response.status}`, error?.message ?? fallbackMessage);
  }

  return data as T;
}

function parseJson(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

/** 오류 객체를 화면에 보여줄 메시지로 바꾼다. */
export function errorMessage(err: unknown, fallback = "요청이 실패했습니다."): string {
  return err instanceof ApiError ? err.message : fallback;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown, extraHeaders?: Record<string, string>) =>
    request<T>("POST", path, body, extraHeaders),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
};
