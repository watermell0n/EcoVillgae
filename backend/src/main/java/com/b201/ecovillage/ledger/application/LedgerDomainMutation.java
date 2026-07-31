package com.b201.ecovillage.ledger.application;

/**
 * A stock, inventory, or other domain mutation that must commit with its ledger transaction.
 * Implementations must only mutate persistence state in {@link #apply()}.
 */
public interface LedgerDomainMutation {

    void validate();

    void apply();
}
