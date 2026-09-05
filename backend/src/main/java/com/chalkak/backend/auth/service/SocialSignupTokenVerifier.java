package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;

public interface SocialSignupTokenVerifier {

    VerifiedSocialSignupToken verify(String signupToken);
}
