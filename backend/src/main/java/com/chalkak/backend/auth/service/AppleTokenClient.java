package com.chalkak.backend.auth.service;

public interface AppleTokenClient {

    AppleTokenExchangeResult exchangeAuthorizationCode(String authorizationCode);

    void revokeRefreshToken(String refreshToken, String clientId);
}
