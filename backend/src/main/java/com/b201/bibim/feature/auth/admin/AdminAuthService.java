package com.b201.bibim.feature.auth.admin;

import java.util.UUID;

import com.b201.bibim.domain.user.UserEntity;
import com.b201.bibim.domain.user.UserRepository;
import com.b201.bibim.feature.auth.admin.AdminAuthDto.AdminResponse;
import com.b201.bibim.feature.auth.admin.AdminAuthDto.LoginRequest;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.global.security.AuthSessionService;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public AdminAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        UserEntity admin = userRepository
                .findByLoginIdAndRoleAndStatus(request.loginId().trim(), "ADMIN", "ACTIVE")
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .orElseThrow(this::invalidCredentials);
        return new LoginResult(toResponse(admin), authSessionService.createAdminSession(admin));
    }

    @Transactional(readOnly = true)
    public AdminResponse me(UUID adminId) {
        UserEntity admin = userRepository.findById(adminId)
                .filter(user -> "ADMIN".equals(user.getRole()))
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
        return toResponse(admin);
    }

    private AdminResponse toResponse(UserEntity admin) {
        return new AdminResponse(admin.getId(), admin.getDisplayName());
    }

    private ApiException invalidCredentials() {
        return new ApiException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 올바르지 않습니다."
        );
    }

    public record LoginResult(AdminResponse response, String token) {
    }
}
