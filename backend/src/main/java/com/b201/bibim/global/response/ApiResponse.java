package com.b201.bibim.global.response;

public record ApiResponse<T>(T data, ApiMeta meta) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(data, new ApiMeta(requestId));
    }
}

