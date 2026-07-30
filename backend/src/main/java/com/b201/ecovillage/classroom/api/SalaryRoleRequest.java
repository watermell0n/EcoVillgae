package com.b201.ecovillage.classroom.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SalaryRoleRequest(
    @NotBlank String roleCode,
    @NotBlank @Size(max = 80) String name,
    @Min(0) long grossAmount,
    Map<String, Object> deductions,
    int sortOrder
) {
}
