package com.b201.ecovillage.classroom.application;

import org.springframework.http.HttpStatus;

public class ClassroomOperationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ClassroomOperationException(HttpStatus status, String code, String message) {
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
