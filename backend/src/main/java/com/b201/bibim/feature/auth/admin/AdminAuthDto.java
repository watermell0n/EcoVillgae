package com.b201.bibim.feature.auth.admin;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AdminAuthDto {

    private AdminAuthDto() {
    }

    public record LoginRequest(
            @NotBlank(message = "아이디를 입력해 주세요.")
            @Size(max = 100, message = "아이디가 너무 깁니다.")
            String loginId,
            @NotBlank(message = "비밀번호를 입력해 주세요.")
            @Size(max = 100, message = "비밀번호가 너무 깁니다.")
            String password
    ) {
    }

    public record AdminResponse(
            UUID adminId,
            String displayName
    ) {
    }
}
