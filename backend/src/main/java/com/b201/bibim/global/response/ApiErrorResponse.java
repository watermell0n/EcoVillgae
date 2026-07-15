package com.b201.bibim.global.response;

import java.util.Map;

public record ApiErrorResponse(ApiError error, ApiMeta meta) {

    public static ApiErrorResponse of(
            String code,
            String message,
            Map<String, Object> details,
            String requestId
    ) {
        return new ApiErrorResponse(
                new ApiError(code, message, Map.copyOf(details)),
                new ApiMeta(requestId)
        );
    }
}
