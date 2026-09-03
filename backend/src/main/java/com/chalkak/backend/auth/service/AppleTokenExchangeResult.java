package com.chalkak.backend.auth.service;

public record AppleTokenExchangeResult(
        String idToken,
        String refreshToken,
        String clientId
) {
}
