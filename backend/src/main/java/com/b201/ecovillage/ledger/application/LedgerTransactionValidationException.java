package com.b201.ecovillage.ledger.application;

public class LedgerTransactionValidationException extends RuntimeException {

    private final String code;

    public LedgerTransactionValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
