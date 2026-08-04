package com.b201.ecovillage.realtime.application;

public class RealtimeProtocolException extends RuntimeException {

    private final String code;

    public RealtimeProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
