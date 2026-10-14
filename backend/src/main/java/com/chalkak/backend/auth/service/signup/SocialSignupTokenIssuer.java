package com.chalkak.backend.auth.service.signup;

import com.chalkak.backend.auth.domain.identity.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.signup.IssuedSocialSignupToken;
import java.util.UUID;

public interface SocialSignupTokenIssuer {

    IssuedSocialSignupToken issue(
            VerifiedSocialIdentity identity,
            UUID uploadId
    );
}
