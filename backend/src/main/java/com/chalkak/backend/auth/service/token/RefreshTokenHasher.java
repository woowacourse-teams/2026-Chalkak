package com.chalkak.backend.auth.service.token;

public interface RefreshTokenHasher {

    String encode(String refreshToken);
}
