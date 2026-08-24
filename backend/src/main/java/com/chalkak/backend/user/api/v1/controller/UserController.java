package com.chalkak.backend.user.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.user.api.v1.dto.request.UserSignatureUpdateRequest;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureResponse;
import com.chalkak.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 사용자는 {@code X-User-Id} 헤더로 식별한다. Spring Security 도입 전까지 쓰는 임시 수단이며 헤더 값을 검증 없이 신뢰하므로,
 * 누구나 남의 계정을 조작할 수 있어 {@code prod}에서는 컨트롤러를 등록하지 않는다.
 *
 * <p>Security 도입 시 프로파일 제한을 없애고 리졸버가 인증 주체를 읽도록 바꾼다. 컨트롤러 시그니처는 그대로 둔다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Profile("!prod")
public class UserController {

    private final UserService userService;

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@LoginUser AuthenticatedUser loginUser) {
        userService.withdraw(loginUser.userId());

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/signature")
    public ResponseEntity<UserSignatureResponse> updateSignature(
            @LoginUser AuthenticatedUser loginUser,
            @Valid @RequestBody UserSignatureUpdateRequest request
    ) {
        String imageUrl = userService.updateSignature(
                loginUser.userId(),
                request.signatureOriginalUploadId()
        );

        return ResponseEntity.ok(new UserSignatureResponse(imageUrl));
    }
}
