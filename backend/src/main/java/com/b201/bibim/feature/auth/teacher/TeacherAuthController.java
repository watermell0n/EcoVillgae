package com.b201.bibim.feature.auth.teacher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.auth.teacher.TeacherAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.teacher.TeacherAuthDto.TeacherResponse;
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
@RequestMapping("/api/v1/auth")
public class TeacherAuthController {

    private final TeacherAuthService teacherAuthService;
    private final AuthCookieService authCookieService;

    public TeacherAuthController(
            TeacherAuthService teacherAuthService,
            AuthCookieService authCookieService
    ) {
        this.teacherAuthService = teacherAuthService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/teacher/login")
    ResponseEntity<ApiResponse<TeacherResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        TeacherAuthService.LoginResult result = teacherAuthService.login(request);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieService.create(AuthRole.TEACHER, result.token()).toString()
                )
                .body(ApiResponse.success(result.response(), requestId(servletRequest)));
    }

    @GetMapping("/me")
    ApiResponse<TeacherResponse> me(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(teacherAuthService.me(principal.id()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
