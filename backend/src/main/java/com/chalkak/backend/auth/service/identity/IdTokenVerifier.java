package com.chalkak.backend.auth.service.identity;

import com.chalkak.backend.auth.domain.identity.SocialProvider;
import com.chalkak.backend.auth.domain.identity.VerifiedSocialIdentity;

public interface IdTokenVerifier {

    SocialProvider getProvider();

    VerifiedSocialIdentity verify(String idToken, String rawNonce);
}
