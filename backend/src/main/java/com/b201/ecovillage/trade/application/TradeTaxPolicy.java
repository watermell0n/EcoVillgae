package com.b201.ecovillage.trade.application;

import java.util.UUID;

public record TradeTaxPolicy(
    int rateBps,
    UUID policyVersionId,
    int version,
    String source
) {
}
