package com.b201.ecovillage.common.api;

import java.time.Instant;
import java.util.UUID;

public record ApiMeta(
    UUID requestId,
    UUID correlationId,
    Instant serverTime
) {
}
