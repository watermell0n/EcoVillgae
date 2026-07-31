package com.b201.ecovillage.store.domain;

public class InsufficientStoreStockException extends RuntimeException {

    private final int availableQuantity;
    private final int requestedQuantity;

    public InsufficientStoreStockException(int availableQuantity, int requestedQuantity) {
        super("store stock is insufficient");
        this.availableQuantity = availableQuantity;
        this.requestedQuantity = requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }
}
