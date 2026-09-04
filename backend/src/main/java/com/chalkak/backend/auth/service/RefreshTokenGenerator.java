package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.GeneratedRefreshToken;

public interface RefreshTokenGenerator {

    GeneratedRefreshToken generateToken();
}
