package com.b201.ecovillage.identity.application;

public class InvalidLoginRequestException extends RuntimeException {

    public InvalidLoginRequestException(String message) {
        super(message);
    }
}
