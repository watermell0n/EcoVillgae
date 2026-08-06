package com.b201.ecovillage.classroom.api;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record RosterAndSalariesRequest(
    @Min(0) Long expectedStateVersion,
    @Pattern(regexp = "^SCR-[A-Z]+-\\d{3}$") String screenId,
    @Pattern(regexp = "^UIA-[A-Z]+-\\d{3}$") String uiActionId,
    Instant clientOccurredAt,
    @NotEmpty List<@Valid StudentRosterRequest> students,
    List<@Valid SalaryRoleRequest> salaryRoles,
    @Min(0) Long expectedPolicyVersion,

    /**
     * true면 이 요청을 명단 전체로 봅니다. 요청에 없는 학생은 종료되고, 함께 보낸 급여 역할
     * 목록에 없는 역할은 비활성으로 내려갑니다. 교사 화면의 "명단 저장"이 이 방식을 씁니다.
     *
     * <p>생략하거나 false면 지금까지처럼 보낸 학생만 추가·수정합니다. 학생 한 명씩 등록하는
     * 흐름이 기존 명단을 지우지 않도록 기본값을 유지합니다.
     */
    Boolean replaceRoster
) {
}
