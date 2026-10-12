package com.chalkak.backend.auth.service.token;

import com.chalkak.backend.auth.domain.token.GeneratedRefreshToken;

public interface RefreshTokenGenerator {

    GeneratedRefreshToken generateToken();
}
