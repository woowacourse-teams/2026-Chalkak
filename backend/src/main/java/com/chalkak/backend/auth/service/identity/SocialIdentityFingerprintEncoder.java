package com.chalkak.backend.auth.service.identity;

import com.chalkak.backend.auth.domain.identity.SocialProvider;

public interface SocialIdentityFingerprintEncoder {

    String encode(SocialProvider provider, String subject);
}
