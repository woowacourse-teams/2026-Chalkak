package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;

public interface AppleIdTokenVerifier {

    VerifiedSocialIdentity verify(String idToken, String rawNonce);
}
