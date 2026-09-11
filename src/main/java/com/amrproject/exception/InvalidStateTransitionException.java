package com.amrproject.exception;

public class InvalidStateTransitionException extends RuntimeException {

    private final String code;

    public InvalidStateTransitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
