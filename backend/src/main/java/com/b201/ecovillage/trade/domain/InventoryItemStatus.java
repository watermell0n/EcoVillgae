package com.b201.ecovillage.trade.domain;

/**
 * Availability of an owned inventory row. Equipment is intentionally stored in
 * character_equipment and is not an ownership status.
 */
public enum InventoryItemStatus {
    AVAILABLE,
    LOCKED_TRADE
}
