package com.b201.ecovillage.report.api;

import java.time.Instant;

public record ReportPeriodResponse(
    Instant from,
    Instant to
) {
}
