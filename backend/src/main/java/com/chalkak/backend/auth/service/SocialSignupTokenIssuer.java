package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import java.util.UUID;

public interface SocialSignupTokenIssuer {

    IssuedSocialSignupToken issue(
            VerifiedSocialIdentity identity,
            UUID uploadId
    );
}
