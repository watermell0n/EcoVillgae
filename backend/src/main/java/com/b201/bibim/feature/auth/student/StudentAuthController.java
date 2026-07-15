package com.b201.bibim.feature.auth.student;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.auth.student.StudentAuthDto.EnterRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.EnterResponse;
import com.b201.bibim.feature.auth.student.StudentAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.SetupRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.StudentResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthCookieService;
import com.b201.bibim.global.security.AuthRole;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class StudentAuthController {

    private final StudentAuthService studentAuthService;
    private final AuthCookieService authCookieService;

    public StudentAuthController(
            StudentAuthService studentAuthService,
            AuthCookieService authCookieService
    ) {
        this.studentAuthService = studentAuthService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/auth/enter")
    ApiResponse<EnterResponse> enter(
            @Valid @RequestBody EnterRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success(studentAuthService.enter(request), requestId(servletRequest));
    }

    @PostMapping("/auth/setup")
    ResponseEntity<ApiResponse<StudentResponse>> setup(
            @Valid @RequestBody SetupRequest request,
            HttpServletRequest servletRequest
    ) {
        return authenticatedResponse(studentAuthService.setup(request), servletRequest);
    }

    @PostMapping("/auth/login")
    ResponseEntity<ApiResponse<StudentResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return authenticatedResponse(studentAuthService.login(request), servletRequest);
    }

    @GetMapping("/me")
    ApiResponse<StudentResponse> me(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(studentAuthService.me(principal.id()), requestId(request));
    }

    private ResponseEntity<ApiResponse<StudentResponse>> authenticatedResponse(
            StudentAuthService.LoginResult result,
            HttpServletRequest request
    ) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieService.create(AuthRole.STUDENT, result.token()).toString()
                )
                .body(ApiResponse.success(result.response(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
