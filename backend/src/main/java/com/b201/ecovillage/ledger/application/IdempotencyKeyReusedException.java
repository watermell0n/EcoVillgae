package com.b201.ecovillage.ledger.application;

public class IdempotencyKeyReusedException extends LedgerTransactionValidationException {

    public IdempotencyKeyReusedException() {
        super(
            "IDEMPOTENCY_KEY_REUSED",
            "idempotency key was already used with a different request"
        );
    }
}
