package com.chalkak.backend.user.api.v1.controller;

import com.chalkak.backend.auth.api.support.AuthenticatedUser;
import com.chalkak.backend.auth.api.support.LoginUser;
import com.chalkak.backend.user.api.v1.docs.UserApiDocs;
import com.chalkak.backend.user.api.v1.dto.request.UserSignatureUpdateRequest;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureDetailResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureResponse;
import com.chalkak.backend.user.api.v1.dto.response.UserSignatureUploadResponse;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.service.UserSignatureResult;
import com.chalkak.backend.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Profile("!prod")
public class UserController implements UserApiDocs {

    private final UserService userService;

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@LoginUser AuthenticatedUser loginUser) {
        userService.withdraw(loginUser.userId());

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/me/signature/uploads")
    public ResponseEntity<UserSignatureUploadResponse> createSignatureUpload(
            @LoginUser AuthenticatedUser loginUser
    ) {
        SignatureImageUpload upload = userService.createSignatureUpload(loginUser.userId());

        return ResponseEntity.ok(UserSignatureUploadResponse.from(upload));
    }

    @Override
    @GetMapping("/me/signature")
    public ResponseEntity<UserSignatureDetailResponse> getSignature(
            @LoginUser AuthenticatedUser loginUser
    ) {
        UserSignatureResult result = userService.getSignature(loginUser.userId());

        return ResponseEntity.ok(UserSignatureDetailResponse.from(result));
    }

    @Override
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
