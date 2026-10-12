package com.chalkak.backend.auth.service.signup;

import com.chalkak.backend.auth.domain.signup.VerifiedSocialSignupToken;

public interface SocialSignupTokenVerifier {

    VerifiedSocialSignupToken verify(String signupToken);
}
