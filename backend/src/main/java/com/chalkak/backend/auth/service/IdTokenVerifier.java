package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;

public interface IdTokenVerifier {

    SocialProvider getProvider();

    VerifiedSocialIdentity verify(String idToken);
}
