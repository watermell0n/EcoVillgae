package com.b201.ecovillage.common.api;

import java.util.List;

public record ApiError(
    String code,
    String message,
    boolean retryable,
    List<ApiErrorDetail> details
) {
}
