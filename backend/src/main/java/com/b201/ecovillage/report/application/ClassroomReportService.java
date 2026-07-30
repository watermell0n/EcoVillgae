package com.b201.ecovillage.report.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.report.repository.ClassroomReportProjectionRepository;

@Service
public class ClassroomReportService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomReportProjectionRepository repository;
    private final Clock clock;

    public ClassroomReportService(
        ClassroomAuthorizationService authorizationService,
        ClassroomReportProjectionRepository repository,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClassroomReportView get(
        String teacherSubject,
        UUID classroomId,
        Instant from,
        Instant to
    ) {
        validate(classroomId, from, to);
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.READ_CLASSROOM_REPORT,
            null
        );
        ClassroomReportAggregate aggregate = repository.aggregate(
            classroomId,
            from,
            to
        );
        long totalCurrency = Math.addExact(
            aggregate.walletBalanceAmount(),
            aggregate.savingsPrincipalAmount()
        );
        long averageWallet = aggregate.activeStudentCount() == 0
            ? 0
            : aggregate.walletBalanceAmount() / aggregate.activeStudentCount();
        return new ClassroomReportView(
            classroomId,
            from,
            to,
            totalCurrency,
            aggregate.walletBalanceAmount(),
            averageWallet,
            aggregate.spendingAmount(),
            aggregate.savingsPrincipalAmount(),
            aggregate.publicFundBalance(),
            aggregate.activeStudentCount(),
            aggregate.quizParticipantCount(),
            aggregate.quizSubmissionCount(),
            clock.instant()
        );
    }

    private static void validate(
        UUID classroomId,
        Instant from,
        Instant to
    ) {
        if (
            classroomId == null
                || from == null
                || to == null
                || !from.isBefore(to)
        ) {
            throw new ClassroomOperationException(
                HttpStatus.BAD_REQUEST,
                "INVALID_REPORT_PERIOD",
                "from must be earlier than to."
            );
        }
    }
}
