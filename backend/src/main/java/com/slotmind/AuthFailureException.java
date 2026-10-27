package com.slotmind;

public class AuthFailureException extends RuntimeException {
    private final String code;

    public AuthFailureException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
