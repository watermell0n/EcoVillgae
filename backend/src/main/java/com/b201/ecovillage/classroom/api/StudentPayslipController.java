package com.b201.ecovillage.classroom.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.classroom.application.StudentPayslipQueryService;
import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;

@Validated
@RestController
@RequestMapping("/classrooms/{classroomId}/students/me/payslips")
public class StudentPayslipController {

    private final StudentPayslipQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentPayslipController(
        StudentPayslipQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StudentPayslipResponse>> findMine(
        @PathVariable UUID classroomId,
        @RequestParam(defaultValue = "24") @Min(1) @Max(100) int limit,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        var payslips = queryService.findMine(
            principal == null ? null : principal.getName(),
            classroomId,
            limit
        ).stream().map(item -> new StudentPayslipResponse.PayslipItem(
            item.resourceId(),
            item.periodKey(),
            item.roleName(),
            "ISSUED",
            1,
            0L,
            item.grossAmount(),
            item.deductionAmount(),
            item.taxAmount(),
            item.netAmount(),
            item.issuedAt()
        )).toList();
        return ResponseEntity.ok(new ApiResponse<>(
            new StudentPayslipResponse(payslips),
            new ApiMeta(
                uuidGenerator.generate(),
                correlationId == null ? uuidGenerator.generate() : correlationId,
                clock.instant()
            )
        ));
    }
}
