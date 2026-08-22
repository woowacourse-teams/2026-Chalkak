package com.chalkak.backend.user.api.v1.controller;

import com.chalkak.backend.user.service.UserService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code X-User-Id} 헤더는 Spring Security 도입 전까지 쓰는 임시 수단이다. 헤더 값을 검증 없이 신뢰하므로 누구나 남의 계정을 조작할 수 있어
 * {@code prod}에서는 컨트롤러를 등록하지 않는다.
 *
 * <p>Security 도입 시 프로파일 제한을 없애고 헤더 파라미터를 인증 주체 주입으로 교체한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Profile("!prod")
public class UserController {

    private final UserService userService;

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@RequestHeader("X-User-Id") UUID userId) {
        userService.withdraw(userId);

        return ResponseEntity.noContent().build();
    }
}
