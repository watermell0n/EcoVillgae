package com.b201.ecovillage.identity.api;

import java.time.Clock;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.StudentLoginCommand;
import com.b201.ecovillage.identity.application.StudentLoginResult;
import com.b201.ecovillage.identity.application.StudentLoginService;

@RestController
@RequestMapping("/auth/student-sessions")
public class StudentSessionController {

    private final StudentLoginService studentLoginService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentSessionController(
        StudentLoginService studentLoginService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.studentLoginService = studentLoginService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StudentSessionResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody StudentSessionRequest request,
        HttpServletRequest httpRequest
    ) {
        StudentLoginResult result = studentLoginService.login(
            new StudentLoginCommand(
                request.classroomCode(),
                request.attendanceNumber(),
                request.nickname(),
                request.password(),
                request.deviceId(),
                httpRequest.getRemoteAddr()
            )
        );
        StudentSessionResponse data = new StudentSessionResponse(
            result.accessToken(),
            result.refreshToken(),
            result.expiresAt(),
            result.classroomId(),
            result.currencyName(),
            result.walletBalance(),
            result.studentId(),
            "STU"
        );
        ApiMeta meta = new ApiMeta(
            requestId,
            correlationId == null ? uuidGenerator.generate() : correlationId,
            clock.instant()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(data, meta));
    }
}
