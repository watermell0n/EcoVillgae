package com.b201.ecovillage.common.api;

public record ApiErrorDetail(
    String field,
    String reason
) {
}
