package com.b201.ecovillage.classroom.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.domain.SalaryRole;
import com.b201.ecovillage.classroom.repository.PayslipRepository;
import com.b201.ecovillage.classroom.repository.SalaryRoleRepository;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class StudentPayslipQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final PayslipRepository payslipRepository;
    private final SalaryRoleRepository salaryRoleRepository;

    public StudentPayslipQueryService(
        ClassroomAuthorizationService authorizationService,
        PayslipRepository payslipRepository,
        SalaryRoleRepository salaryRoleRepository
    ) {
        this.authorizationService = authorizationService;
        this.payslipRepository = payslipRepository;
        this.salaryRoleRepository = salaryRoleRepository;
    }

    @Transactional(readOnly = true)
    public java.util.List<StudentPayslipView> findMine(
        String subject,
        UUID classroomId,
        int limit
    ) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        var payslips = payslipRepository
            .findAllByClassroomIdAndClassroomStudentIdOrderByIssuedAtDesc(
                classroomId,
                studentId,
                PageRequest.of(0, limit)
            );
        Map<UUID, SalaryRole> roles = salaryRoleRepository.findAllById(
            payslips.stream()
                .map(item -> item.getSalaryRoleId())
                .filter(Objects::nonNull)
                .distinct()
                .toList()
        ).stream().collect(Collectors.toMap(SalaryRole::getId, Function.identity()));

        return payslips.stream().map(item -> {
            SalaryRole role = roles.get(item.getSalaryRoleId());
            return new StudentPayslipView(
                item.getId(),
                item.getPeriodKey(),
                role == null ? "급여" : role.getName(),
                item.getGrossAmount(),
                item.getDeductionAmount(),
                item.getTaxAmount(),
                item.getNetAmount(),
                item.getIssuedAt()
            );
        }).toList();
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "학생 인증 정보를 확인할 수 없습니다."
            );
        }
    }
}
