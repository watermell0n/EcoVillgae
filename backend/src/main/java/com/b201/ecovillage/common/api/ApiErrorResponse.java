package com.b201.ecovillage.common.api;

public record ApiErrorResponse(
    ApiError error,
    ApiMeta meta
) {
}
