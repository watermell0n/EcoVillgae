package com.b201.ecovillage.trade.domain;

public class TradeOperationException extends RuntimeException {

    private final String code;

    public TradeOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
