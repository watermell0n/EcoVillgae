package com.b201.ecovillage.identity.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.identity.application.TeacherLoginResult;
import com.b201.ecovillage.identity.application.TeacherRegistrationService;

@RestController
@RequestMapping("/auth/teacher-registrations")
public class TeacherRegistrationController {

    private final TeacherRegistrationService registrationService;
    private final TeacherSessionResponseFactory responseFactory;

    public TeacherRegistrationController(
        TeacherRegistrationService registrationService,
        TeacherSessionResponseFactory responseFactory
    ) {
        this.registrationService = registrationService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TeacherRegistrationRequest request
    ) {
        TeacherLoginResult result = registrationService.register(
            request.schoolCode(),
            request.displayName(),
            request.email(),
            request.password()
        );
        return responseFactory.created(requestId, correlationId, result);
    }
}
