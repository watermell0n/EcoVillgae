package com.b201.ecovillage.classroom.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.b201.ecovillage.classroom.domain.Payslip;
import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.PayslipRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

class StudentPayslipQueryServiceTest {

    private ClassroomAuthorizationService authorizationService;
    private PayslipRepository payslipRepository;
    private SalaryRoleRepository salaryRoleRepository;
    private StudentPayslipQueryService service;

    @BeforeEach
    void setUp() {
        authorizationService = mock(ClassroomAuthorizationService.class);
        payslipRepository = mock(PayslipRepository.class);
        salaryRoleRepository = mock(SalaryRoleRepository.class);
        service = new StudentPayslipQueryService(
            authorizationService,
            payslipRepository,
            salaryRoleRepository
        );
    }

    @Test
    void returnsRealPayslipAmountsAndRoleNameInNewestFirstOrder() {
        UUID classroomId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        Instant issuedAt = Instant.parse("2026-08-09T03:00:00Z");
        Payslip payslip = Payslip.issue(
            UUID.randomUUID(),
            classroomId,
            studentId,
            roleId,
            "2026-08-2주차",
            1_000,
            50,
            95,
            UUID.randomUUID(),
            UUID.randomUUID(),
            issuedAt
        );
        SalaryRole role = SalaryRole.create(
            roleId,
            classroomId,
            "CLEANER",
            "환경 지킴이",
            1_000,
            Map.of(),
            1,
            issuedAt.minusSeconds(3_600)
        );
        when(payslipRepository.findAllByClassroomIdAndClassroomStudentIdOrderByIssuedAtDesc(
            eq(classroomId),
            eq(studentId),
            any(Pageable.class)
        )).thenReturn(List.of(payslip));
        when(salaryRoleRepository.findAllById(List.of(roleId))).thenReturn(List.of(role));

        List<StudentPayslipView> result = service.findMine(studentId.toString(), classroomId, 24);

        assertEquals(1, result.size());
        StudentPayslipView view = result.getFirst();
        assertEquals("환경 지킴이", view.roleName());
        assertEquals("2026-08-2주차", view.periodKey());
        assertEquals(1_000, view.grossAmount());
        assertEquals(50, view.deductionAmount());
        assertEquals(95, view.taxAmount());
        assertEquals(855, view.netAmount());
        assertEquals(issuedAt, view.issuedAt());
        verify(authorizationService).authorize(
            studentId.toString(),
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
    }

    @Test
    void rejectsRequestWithoutAStudentSession() {
        ClassroomOperationException exception = assertThrows(
            ClassroomOperationException.class,
            () -> service.findMine("teacher-subject", UUID.randomUUID(), 24)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("AUTH_REQUIRED", exception.getCode());
    }
}
