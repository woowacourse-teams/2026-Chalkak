package com.chalkak.backend.auth.service.apple;

public record AppleTokenExchangeResult(
        String idToken,
        String refreshToken) {
}
