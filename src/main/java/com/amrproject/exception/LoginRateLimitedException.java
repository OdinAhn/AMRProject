package com.amrproject.exception;

/** 36단계: 로그인 실패가 너무 많아 잠시 막힘 — 429와 Retry-After(초)로 응답한다. */
public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("로그인 실패가 너무 많습니다. " + Math.max(1, (retryAfterSeconds + 59) / 60) + "분 후에 다시 시도하세요.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getCode() {
        return "LOGIN_RATE_LIMITED";
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
