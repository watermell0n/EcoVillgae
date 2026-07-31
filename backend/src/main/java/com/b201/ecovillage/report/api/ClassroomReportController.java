package com.b201.ecovillage.report.api;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.report.application.ClassroomReportService;
import com.b201.ecovillage.report.application.ClassroomReportView;

@RestController
public class ClassroomReportController {

    private final ClassroomReportService service;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ClassroomReportController(
        ClassroomReportService service,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.service = service;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping("/classrooms/{classroomId}/reports")
    public ApiResponse<ClassroomReportResponse> get(
        @PathVariable UUID classroomId,
        @RequestParam Instant from,
        @RequestParam Instant to,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        ClassroomReportView view = service.get(
            principal == null ? null : principal.getName(),
            classroomId,
            from,
            to
        );
        return new ApiResponse<>(
            new ClassroomReportResponse(
                view.classroomId(),
                new ReportPeriodResponse(view.from(), view.to()),
                view.totalCurrencyAmount(),
                view.walletBalanceAmount(),
                view.averageWalletBalance(),
                view.spendingAmount(),
                view.savingsPrincipalAmount(),
                view.publicFundBalance(),
                view.activeStudentCount(),
                view.quizParticipantCount(),
                view.quizSubmissionCount(),
                view.generatedAt()
            ),
            new ApiMeta(requestId, correlation, clock.instant())
        );
    }
}
