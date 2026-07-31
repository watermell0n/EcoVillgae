package com.b201.ecovillage.audit.api;

import java.util.List;

public record AuditEventsResponse(List<AuditEventResponse> items) {
    public AuditEventsResponse {
        items = List.copyOf(items);
    }
}
