package com.chalkak.backend.auth.infrastructure.infra.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.GeneratedRefreshToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRandomRefreshTokenProviderTest {

    private static final String BASE64_URL_PATTERN = "^[A-Za-z0-9_-]{43}$";
    private static final String TOKEN_HASH_PATTERN = "^[0-9a-f]{64}$";

    private final SecureRandomRefreshTokenProvider provider =
            new SecureRandomRefreshTokenProvider();

    @Test
    @DisplayName("생성한 토큰은 패딩 없는 43자 Base64URL 문자열이다")
    void generateToken_always_returnsBase64UrlValueWithoutPadding() {
        // when
        GeneratedRefreshToken generatedToken = provider.generateToken();

        // then
        assertThat(generatedToken.value()).matches(BASE64_URL_PATTERN);
    }

    @Test
    @DisplayName("토큰을 생성할 때마다 서로 다른 값을 만든다")
    void generateToken_calledTwice_returnsDifferentValues() {
        // when
        GeneratedRefreshToken first = provider.generateToken();
        GeneratedRefreshToken second = provider.generateToken();

        // then
        assertThat(first.value()).isNotEqualTo(second.value());
        assertThat(first.tokenHash()).isNotEqualTo(second.tokenHash());
    }

    @Test
    @DisplayName("생성한 토큰의 해시는 소문자 hex 64자이며 토큰 값의 해시와 일치한다")
    void generateToken_always_returnsHashOfValue() {
        // when
        GeneratedRefreshToken generatedToken = provider.generateToken();

        // then
        assertThat(generatedToken.tokenHash()).matches(TOKEN_HASH_PATTERN);
        assertThat(generatedToken.tokenHash())
                .isEqualTo(provider.encode(generatedToken.value()));
    }

    @Test
    @DisplayName("같은 토큰은 항상 같은 해시로 변환한다")
    void encode_sameToken_returnsSameHash() {
        // given
        String refreshToken = provider.generateToken().value();

        // when
        String hash = provider.encode(refreshToken);

        // then
        assertThat(hash).isEqualTo(provider.encode(refreshToken));
    }

    @Test
    @DisplayName("다른 토큰은 다른 해시로 변환한다")
    void encode_differentTokens_returnsDifferentHashes() {
        // when
        String hash = provider.encode("refresh-token-a");
        String otherHash = provider.encode("refresh-token-b");

        // then
        assertThat(hash).isNotEqualTo(otherHash);
    }
}
