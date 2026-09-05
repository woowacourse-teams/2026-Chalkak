package com.chalkak.backend.auth.service;

public interface RefreshTokenHasher {

    String encode(String refreshToken);
}
