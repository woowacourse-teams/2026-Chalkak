package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;

public interface SocialIdentityFingerprintEncoder {

    String encode(SocialProvider provider, String subject);
}
