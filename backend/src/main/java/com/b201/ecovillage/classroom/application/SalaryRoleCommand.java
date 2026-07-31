package com.b201.ecovillage.classroom.application;

import java.util.Map;

public record SalaryRoleCommand(
    String roleCode,
    String name,
    long grossAmount,
    Map<String, Object> deductions,
    int sortOrder
) {
}
