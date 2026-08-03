package com.b201.ecovillage.common.api;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiMeta(
    UUID requestId,
    UUID correlationId,
    Instant serverTime,
    PageMeta page
) {

    public ApiMeta(UUID requestId, UUID correlationId, Instant serverTime) {
        this(requestId, correlationId, serverTime, null);
    }

    public record PageMeta(String nextCursor, boolean hasMore) {
    }
}
