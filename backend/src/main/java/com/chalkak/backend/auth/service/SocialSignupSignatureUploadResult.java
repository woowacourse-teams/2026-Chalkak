package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.user.repository.SignatureImageUpload;

public record SocialSignupSignatureUploadResult(
        SignatureImageUpload upload,
        IssuedSocialSignupToken signupToken
) {
}
