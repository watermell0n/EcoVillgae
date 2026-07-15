package com.b201.bibim.global.response;

import java.util.Map;

public record ApiError(String code, String message, Map<String, Object> details) {
}
