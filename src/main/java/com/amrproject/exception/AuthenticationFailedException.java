package com.amrproject.exception;

public class AuthenticationFailedException extends RuntimeException {

    private final String code;

    public AuthenticationFailedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
