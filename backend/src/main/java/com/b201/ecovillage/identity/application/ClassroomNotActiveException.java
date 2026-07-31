package com.b201.ecovillage.identity.application;

public class ClassroomNotActiveException extends RuntimeException {

    public ClassroomNotActiveException() {
        super("Classroom does not allow student access");
    }
}
