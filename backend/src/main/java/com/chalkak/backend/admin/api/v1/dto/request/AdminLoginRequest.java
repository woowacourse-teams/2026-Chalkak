package com.chalkak.backend.admin.api.v1.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(
        @NotBlank(message = "관리자 아이디를 입력해 주세요.")
        @Size(max = 100, message = "관리자 아이디 형식이 올바르지 않습니다.")
        String username,

        @NotBlank(message = "관리자 비밀번호를 입력해 주세요.")
        @Size(max = 200, message = "관리자 비밀번호 형식이 올바르지 않습니다.")
        String password
) {
}
