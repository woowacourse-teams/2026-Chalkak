package com.chalkak.backend.auth.service;

public interface AppleRefreshTokenCipher {

    String encrypt(String refreshToken);

    String decrypt(String encryptedRefreshToken);
}
