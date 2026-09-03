package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.chalkak.backend.auth.infrastructure.infra.oidc.apple.AppleOidcProperties;
import com.chalkak.backend.auth.service.AppleTokenExchangeResult;
import com.chalkak.backend.exception.UnauthorizedException;
import java.io.IOException;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class AppleHttpTokenClientTest {

    private static final URI TOKEN_URI = URI.create("https://appleid.test/auth/token");
    private static final URI REVOKE_URI = URI.create("https://appleid.test/auth/revoke");

    private MockRestServiceServer server;
    private AppleHttpTokenClient tokenClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretGenerator clientSecretGenerator = mock(AppleClientSecretGenerator.class);
        when(clientSecretGenerator.generate()).thenReturn("test-client-secret");
        AppleTokenProperties tokenProperties = new AppleTokenProperties(
                "APPLETEAM1",
                "APPLEKEY01",
                "test-private-key",
                TOKEN_URI.toString(),
                REVOKE_URI.toString());
        AppleOidcProperties oidcProperties = new AppleOidcProperties(
                "https://appleid.apple.com",
                "https://appleid.apple.com/auth/keys",
                "com.chalkak.ios");
        tokenClient = new AppleHttpTokenClient(
                builder.build(),
                tokenProperties,
                oidcProperties,
                clientSecretGenerator);
    }

    @AfterEach
    void tearDown() {
        server.verify();
    }

    @Test
    @DisplayName("authorizationCode와 서버 자격증명을 Form으로 보내 Apple 토큰을 반환한다")
    void exchangeAuthorizationCode_validCode_returnsAppleTokens() {
        // Given
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "com.chalkak.ios");
        expectedForm.add("client_secret", "test-client-secret");
        expectedForm.add("code", "valid-authorization-code");
        expectedForm.add("grant_type", "authorization_code");
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess(validTokenResponse(), APPLICATION_JSON));

        // When
        AppleTokenExchangeResult result =
                tokenClient.exchangeAuthorizationCode("valid-authorization-code");

        // Then
        assertThat(result.idToken()).isEqualTo("apple-id-token");
        assertThat(result.refreshToken()).isEqualTo("apple-refresh-token");
        assertThat(result.clientId()).isEqualTo("com.chalkak.ios");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("authorizationCode가 없으면 Apple 토큰 교환을 거부한다")
    void exchangeAuthorizationCode_missingCode_throwsUnauthorizedException(String authorizationCode) {
        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode(authorizationCode))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Apple authorizationCode가 필요합니다.");
    }

    @Test
    @DisplayName("만료되었거나 이미 사용된 authorizationCode는 인증 실패로 처리한다")
    void exchangeAuthorizationCode_invalidGrant_throwsUnauthorizedException() {
        // Given
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode("invalid-code"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("만료되었거나 이미 사용된 Apple authorizationCode입니다.");
    }

    @Test
    @DisplayName("잘못된 client_secret 응답은 서버 설정 오류로 처리한다")
    void exchangeAuthorizationCode_invalidClient_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\"}"));

        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode("valid-code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 요청 설정이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("Apple 토큰 서버의 5xx 응답은 통신 오류로 처리한다")
    void exchangeAuthorizationCode_serverError_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode("valid-code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 서버와 통신할 수 없습니다.");
    }

    @Test
    @DisplayName("Apple 토큰 서버의 네트워크 오류는 통신 오류로 처리한다")
    void exchangeAuthorizationCode_networkError_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(TOKEN_URI))
                .andRespond(request -> {
                    throw new IOException("test network error");
                });

        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode("valid-code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 서버와 통신할 수 없습니다.");
    }

    @ParameterizedTest
    @MethodSource("invalidTokenResponses")
    @DisplayName("Apple 성공 응답에 필수 토큰 정보가 빠지면 응답 오류로 처리한다")
    void exchangeAuthorizationCode_invalidTokenResponse_throwsIllegalStateException(String responseBody) {
        // Given
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(responseBody, APPLICATION_JSON));

        // When & Then
        assertThatThrownBy(() -> tokenClient.exchangeAuthorizationCode("valid-code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 응답이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("전달받은 Client ID와 서버 자격증명을 Form으로 보내 Apple 토큰을 폐기한다")
    void revokeRefreshToken_validRefreshToken_completesRevocation() {
        // Given
        // 설정에 등록된 client_id("com.chalkak.ios")와 다른 값을 전달해, 폐기 요청이
        // 그 값을 무시하고 전달받은 client_id를 그대로 쓰는지 검증한다.
        MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
        expectedForm.add("client_id", "com.chalkak.web");
        expectedForm.add("client_secret", "test-client-secret");
        expectedForm.add("token", "apple-refresh-token");
        expectedForm.add("token_type_hint", "refresh_token");
        server.expect(requestTo(REVOKE_URI))
                .andExpect(method(POST))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess());

        // When & Then
        tokenClient.revokeRefreshToken("apple-refresh-token", "com.chalkak.web");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Refresh Token이 없으면 Apple 토큰 폐기를 거부한다")
    void revokeRefreshToken_missingRefreshToken_throwsIllegalArgumentException(String refreshToken) {
        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(refreshToken, "com.chalkak.ios"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple refresh token이 필요합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    @DisplayName("Client ID가 없으면 Apple 토큰 폐기를 거부한다")
    void revokeRefreshToken_missingClientId_throwsIllegalArgumentException(String clientId) {
        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken("apple-refresh-token", clientId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Apple client_id가 필요합니다.");
    }

    @Test
    @DisplayName("이미 무효한 Refresh Token의 폐기 요청도 서버 설정 오류로 처리한다")
    void revokeRefreshToken_invalidGrant_throwsIllegalStateException() {
        // Given
        // Apple은 이미 폐기된 토큰을 다시 폐기하면 200을 반환한다. invalid_grant가 오는 것은
        // 재시도가 아니라 client_id 불일치 등 진짜 오류이므로 성공으로 취급하지 않는다.
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\"}"));

        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(
                "apple-refresh-token",
                "com.chalkak.ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 폐기 요청이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("본문을 읽을 수 없는 Apple 토큰 폐기 실패는 서버 설정 오류로 처리한다")
    void revokeRefreshToken_unreadableBadRequestBody_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(
                "apple-refresh-token",
                "com.chalkak.ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 폐기 요청이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("잘못된 Apple 토큰 폐기 요청은 서버 설정 오류로 처리한다")
    void revokeRefreshToken_badRequest_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\"}"));

        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(
                "apple-refresh-token",
                "com.chalkak.ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 폐기 요청이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("Apple 토큰 폐기 서버의 5xx 응답은 통신 오류로 처리한다")
    void revokeRefreshToken_serverError_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(REVOKE_URI))
                .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(
                "apple-refresh-token",
                "com.chalkak.ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 서버와 통신할 수 없습니다.");
    }

    @Test
    @DisplayName("Apple 토큰 폐기 서버의 네트워크 오류는 통신 오류로 처리한다")
    void revokeRefreshToken_networkError_throwsIllegalStateException() {
        // Given
        server.expect(requestTo(REVOKE_URI))
                .andRespond(request -> {
                    throw new IOException("test network error");
                });

        // When & Then
        assertThatThrownBy(() -> tokenClient.revokeRefreshToken(
                "apple-refresh-token",
                "com.chalkak.ios"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Apple 토큰 서버와 통신할 수 없습니다.");
    }

    private static Stream<String> invalidTokenResponses() {
        return Stream.of(
                tokenResponse("", "bearer", 3600, "apple-refresh-token", "apple-id-token"),
                tokenResponse("apple-access-token", "unknown", 3600, "apple-refresh-token", "apple-id-token"),
                tokenResponse("apple-access-token", "bearer", 0, "apple-refresh-token", "apple-id-token"),
                tokenResponse("apple-access-token", "bearer", 3600, "", "apple-id-token"),
                tokenResponse("apple-access-token", "bearer", 3600, "apple-refresh-token", "")
        );
    }

    private String validTokenResponse() {
        return tokenResponse(
                "apple-access-token",
                "bearer",
                3600,
                "apple-refresh-token",
                "apple-id-token");
    }

    private static String tokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            String refreshToken,
            String idToken
    ) {
        return """
                {
                  "access_token": "%s",
                  "token_type": "%s",
                  "expires_in": %d,
                  "refresh_token": "%s",
                  "id_token": "%s"
                }
                """.formatted(accessToken, tokenType, expiresIn, refreshToken, idToken);
    }
}
