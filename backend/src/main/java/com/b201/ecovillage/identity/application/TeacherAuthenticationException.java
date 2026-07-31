package com.b201.ecovillage.identity.application;

import org.springframework.http.HttpStatus;

public class TeacherAuthenticationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final boolean retryable;

    public TeacherAuthenticationException(
        HttpStatus status,
        String code,
        String message,
        boolean retryable
    ) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
