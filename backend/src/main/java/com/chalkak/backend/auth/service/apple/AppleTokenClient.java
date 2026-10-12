package com.chalkak.backend.auth.service.apple;

public interface AppleTokenClient {

    AppleTokenExchangeResult exchangeAuthorizationCode(String authorizationCode);

    void revokeRefreshToken(String refreshToken);
}
