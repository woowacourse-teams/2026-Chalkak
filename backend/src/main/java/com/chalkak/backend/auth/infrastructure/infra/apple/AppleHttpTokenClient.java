package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.chalkak.backend.auth.service.AppleTokenClient;
import com.chalkak.backend.auth.service.AppleTokenExchangeResult;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class AppleHttpTokenClient implements AppleTokenClient {

    private static final String AUTHORIZATION_CODE_GRANT_TYPE = "authorization_code";
    private static final String REFRESH_TOKEN_TYPE_HINT = "refresh_token";
    private static final String INVALID_GRANT = "invalid_grant";

    private final RestClient restClient;
    private final URI tokenUri;
    private final URI revokeUri;
    private final AppleClientSecretGenerator clientSecretGenerator;
    private final String clientId;

    public AppleHttpTokenClient(
            RestClient restClient,
            AppleTokenProperties tokenProperties,
            AppleOidcProperties oidcProperties,
            AppleClientSecretGenerator clientSecretGenerator
    ) {
        this.restClient = restClient;
        this.tokenUri = URI.create(tokenProperties.tokenUri());
        this.revokeUri = URI.create(tokenProperties.revokeUri());
        this.clientSecretGenerator = clientSecretGenerator;
        this.clientId = oidcProperties.clientId();
    }

    @Override
    public AppleTokenExchangeResult exchangeAuthorizationCode(String authorizationCode) {
        validateAuthorizationCode(authorizationCode);
        MultiValueMap<String, String> form = createTokenExchangeForm(authorizationCode);

        try {
            AppleTokenResponse response = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            return toResult(response);
        } catch (HttpClientErrorException.BadRequest exception) {
            throw mapBadRequest(exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Apple 토큰 서버와 통신할 수 없습니다.",
                    exception);
        }
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        validateRefreshToken(refreshToken);
        MultiValueMap<String, String> form = createRefreshTokenRevocationForm(refreshToken);

        try {
            restClient.post()
                    .uri(revokeUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.BadRequest exception) {
            validateAlreadyInvalidatedToken(exception);
        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Apple 토큰 서버와 통신할 수 없습니다.",
                    exception);
        }
    }

    private void validateAuthorizationCode(String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "Apple authorizationCode가 필요합니다.");
        }
    }

    private void validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Apple refresh token이 필요합니다.");
        }
    }

    private MultiValueMap<String, String> createTokenExchangeForm(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("code", authorizationCode);
        form.add("grant_type", AUTHORIZATION_CODE_GRANT_TYPE);
        return form;
    }

    private MultiValueMap<String, String> createRefreshTokenRevocationForm(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecretGenerator.generate());
        form.add("token", refreshToken);
        form.add("token_type_hint", REFRESH_TOKEN_TYPE_HINT);
        return form;
    }

    private AppleTokenExchangeResult toResult(AppleTokenResponse response) {
        if (response == null || !response.isValid()) {
            throw new IllegalStateException("Apple 토큰 응답이 올바르지 않습니다.");
        }
        return new AppleTokenExchangeResult(
                response.idToken(),
                response.refreshToken(),
                clientId);
    }

    /**
     * 이미 무효한 RT는 폐기하려던 상태에 이미 도달한 것이므로 정상 종료한다. 이를 실패로 다루면
     * 사용자가 그 토큰 때문에 영영 탈퇴하지 못한다. 반면 {@code invalid_client}는 우리 설정이
     * 틀려 요청이 받아들여지지 않은 것이라 토큰이 그대로 살아 있으므로 그대로 던진다.
     */
    private void validateAlreadyInvalidatedToken(
            HttpClientErrorException.BadRequest exception
    ) {
        AppleErrorResponse response = getErrorResponse(exception);
        if (response != null && INVALID_GRANT.equals(response.error())) {
            return;
        }
        throw new IllegalStateException(
                "Apple 토큰 폐기 요청이 올바르지 않습니다.",
                exception);
    }

    private RuntimeException mapBadRequest(HttpClientErrorException.BadRequest exception) {
        AppleErrorResponse response = getErrorResponse(exception);
        if (response != null && INVALID_GRANT.equals(response.error())) {
            return new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "만료되었거나 이미 사용된 Apple authorizationCode입니다.");
        }
        return new IllegalStateException("Apple 토큰 요청 설정이 올바르지 않습니다.", exception);
    }

    private AppleErrorResponse getErrorResponse(HttpClientErrorException.BadRequest exception) {
        try {
            return exception.getResponseBodyAs(AppleErrorResponse.class);
        } catch (RestClientException ignored) {
            return null;
        }
    }

    private record AppleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("id_token") String idToken
    ) {

        private boolean isValid() {
            return isNotBlank(accessToken)
                    && "bearer".equalsIgnoreCase(tokenType)
                    && expiresIn != null
                    && expiresIn > 0
                    && isNotBlank(refreshToken)
                    && isNotBlank(idToken);
        }

        private boolean isNotBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    private record AppleErrorResponse(String error) {
    }
}
