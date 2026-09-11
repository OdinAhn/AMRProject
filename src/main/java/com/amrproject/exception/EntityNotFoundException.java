package com.amrproject.exception;

public class EntityNotFoundException extends RuntimeException {

    private final String code;

    public EntityNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
