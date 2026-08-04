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
import com.b201.ecovillage.identity.application.TeacherLoginService;

@RestController
@RequestMapping("/auth/teacher-sessions")
public class TeacherSessionController {

    private final TeacherLoginService teacherLoginService;
    private final TeacherSessionResponseFactory responseFactory;

    public TeacherSessionController(
        TeacherLoginService teacherLoginService,
        TeacherSessionResponseFactory responseFactory
    ) {
        this.teacherLoginService = teacherLoginService;
        this.responseFactory = responseFactory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeacherSessionResponse>> create(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody TeacherSessionRequest request
    ) {
        TeacherLoginResult result = teacherLoginService.login(request.idToken());
        return responseFactory.created(requestId, correlationId, result);
    }
}
