package com.b201.ecovillage.vote.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoteOptionRequest(
    @NotBlank @Size(max = 160) String label,
    @Size(max = 2_000) String description,
    @JsonProperty("budget_amount")
    @NotNull
    @PositiveOrZero
    Long budgetAmount
) {
}
