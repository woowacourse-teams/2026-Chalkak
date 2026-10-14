package com.chalkak.backend.auth.service.apple;

public interface AppleAuthorizationCipher {

    String encrypt(String refreshToken);

    String decrypt(String encryptedRefreshToken);
}
