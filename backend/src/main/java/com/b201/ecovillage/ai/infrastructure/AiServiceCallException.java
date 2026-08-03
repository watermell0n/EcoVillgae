package com.b201.ecovillage.ai.infrastructure;

public class AiServiceCallException extends RuntimeException {

    private final String code;

    public AiServiceCallException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiServiceCallException(String code, String message) {
        this(code, message, null);
    }

    public String getCode() {
        return code;
    }
}
