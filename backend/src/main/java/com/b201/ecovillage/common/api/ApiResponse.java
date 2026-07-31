package com.b201.ecovillage.common.api;

public record ApiResponse<T>(
    T data,
    ApiMeta meta
) {
}
