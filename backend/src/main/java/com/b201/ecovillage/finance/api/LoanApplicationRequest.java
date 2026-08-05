package com.b201.ecovillage.finance.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record LoanApplicationRequest(
    Long expectedStateVersion,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt,
    @NotBlank String command,
    UUID applicationId,
    @Min(1) Long amount,
    @Min(1) Integer installments
) { }
