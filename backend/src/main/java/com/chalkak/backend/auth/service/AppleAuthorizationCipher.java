package com.chalkak.backend.auth.service;

public interface AppleAuthorizationCipher {

    String encrypt(String refreshToken);

    String decrypt(String encryptedRefreshToken);
}
