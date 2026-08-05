package com.b201.ecovillage.identity.application;

import org.springframework.http.HttpStatus;

public class StudentRegistrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public StudentRegistrationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
