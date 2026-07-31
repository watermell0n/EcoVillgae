package com.b201.ecovillage.identity.application;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Student credentials are invalid");
    }
}
