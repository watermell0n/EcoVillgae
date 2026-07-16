package com.b201.bibim.feature.auth.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.auth.admin.AdminAuthDto.AdminResponse;
import com.b201.bibim.feature.auth.admin.AdminAuthDto.LoginRequest;
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
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AuthCookieService authCookieService;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            AuthCookieService authCookieService
    ) {
        this.adminAuthService = adminAuthService;
        this.authCookieService = authCookieService;
    }

    @PostMapping("/login")
    ResponseEntity<ApiResponse<AdminResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request
    ) {
        AdminAuthService.LoginResult result = adminAuthService.login(loginRequest);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieService.create(AuthRole.ADMIN, result.token()).toString()
                )
                .body(ApiResponse.success(result.response(), requestId(request)));
    }

    @GetMapping("/me")
    ApiResponse<AdminResponse> me(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(adminAuthService.me(principal.id()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
