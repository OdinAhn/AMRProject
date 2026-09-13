package com.amrproject.exception;

/**
 * 26단계: 인증은 됐지만 요청한 작업장(또는 그 작업장의 작업/site)에 대한 권한이 없을 때 — 403.
 */
public class StationAccessDeniedException extends RuntimeException {

    private final String code;

    public StationAccessDeniedException(String message) {
        super(message);
        this.code = "STATION_ACCESS_DENIED";
    }

    public String getCode() {
        return code;
    }
}
