package com.amrproject.dto.response;

public record ErrorResponse(String code, String message, Object details) {

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(code, message, details);
    }
}
