package com.chalkak.backend.auth.service.signup;

import com.chalkak.backend.auth.domain.signup.IssuedSocialSignupToken;
import com.chalkak.backend.user.repository.SignatureImageUpload;

public record SocialSignupSignatureUploadResult(
        SignatureImageUpload upload,
        IssuedSocialSignupToken signupToken) {
}
