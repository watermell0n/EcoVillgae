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
import com.b201.ecovillage.identity.application.TeacherPasswordLoginService;

@RestController
@RequestMapping("/auth/teacher-password-sessions")
public class TeacherPasswordSessionController {

    private final TeacherPasswordLoginService loginService;
    private final TeacherSessionResponseFactory responseFactory;

    public TeacherPasswordSessionController(
        TeacherPasswordLoginService loginService,
        TeacherSessionResponseFactory responseFactory
    ) {
        this.loginService = loginService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TeacherPasswordSessionRequest request
    ) {
        TeacherLoginResult result = loginService.login(request.email(), request.password());
        return responseFactory.created(requestId, correlationId, result);
    }
}
