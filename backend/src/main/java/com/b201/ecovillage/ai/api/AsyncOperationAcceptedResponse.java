package com.b201.ecovillage.ai.api;

import java.util.UUID;

/**
 * 비동기 작업 접수 응답이다. 계약이 strict라 세 필드만 담는다.
 */
public record AsyncOperationAcceptedResponse(
    UUID operationId,
    String status,
    String statusUrl
) {
}
