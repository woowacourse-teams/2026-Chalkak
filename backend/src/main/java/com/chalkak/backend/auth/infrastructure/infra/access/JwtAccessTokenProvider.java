package com.chalkak.backend.auth.infrastructure.infra.access;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.service.AccessTokenIssuer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public class JwtAccessTokenProvider implements AccessTokenIssuer {

    private static final String PURPOSE = "ACCESS";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final AccessTokenProperties properties;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtAccessTokenProvider(AccessTokenProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtAccessTokenProvider(AccessTokenProperties properties, Clock clock) {
        SecretKey secretKey = new SecretKeySpec(
                HexFormat.of().parseHex(properties.secret()),
                "HmacSHA256");
        this.properties = properties;
        this.clock = clock;
        this.jwtEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<SecurityContext>(secretKey));
        this.jwtDecoder = createJwtDecoder(secretKey, clock);
    }

    @Override
    public IssuedAccessToken issue(UUID userId) {
        return issue(userId, AccessTokenScope.USER);
    }

    @Override
    public IssuedAccessToken issue(UUID subjectId, AccessTokenScope scope) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(subjectId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(PURPOSE_CLAIM, PURPOSE)
                .claim("scope", scope.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new IssuedAccessToken(value, properties.expiration());
    }

    public JwtDecoder jwtDecoder() {
        return jwtDecoder;
    }

    private JwtDecoder createJwtDecoder(SecretKey secretKey, Clock clock) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        // 기본값은 exp가 없는 토큰을 통과시킨다. 그대로 두면 만료되지 않는 토큰이 만들어질 수 있어
        // 1시간 만료라는 전제가 통째로 무너진다.
        timestampValidator.setAllowEmptyExpiryClaim(false);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                this::validateAudience,
                this::validatePurpose,
                this::validateSubject));
        return jwtDecoder;
    }

    /**
     * 회원가입 토큰과 액세스 토큰은 비밀키·audience·purpose로 각각 분리한다. 비밀키를 같은 값으로
     * 잘못 설정해도 5분짜리 회원가입 토큰이 1시간짜리 액세스 토큰으로 통과하지 않게 하기 위해서다.
     */
    private OAuth2TokenValidatorResult validateAudience(Jwt jwt) {
        if (jwt.getAudience().contains(properties.audience())) {
            return OAuth2TokenValidatorResult.success();
        }
        return invalidTokenResult();
    }

    private OAuth2TokenValidatorResult validatePurpose(Jwt jwt) {
        if (PURPOSE.equals(jwt.getClaimAsString(PURPOSE_CLAIM))) {
            return OAuth2TokenValidatorResult.success();
        }
        return invalidTokenResult();
    }

    /**
     * subject는 회원 식별자다. 여기서 걸러 두면 인증을 통과한 토큰의 subject를 신뢰할 수 있어,
     * 뒤에서 형식이 어긋난 값을 만나 500으로 끝나는 대신 401로 응답한다.
     */
    private OAuth2TokenValidatorResult validateSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            return invalidTokenResult();
        }
        try {
            UUID.fromString(subject);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            return invalidTokenResult();
        }
    }

    private OAuth2TokenValidatorResult invalidTokenResult() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "Invalid access token",
                null));
    }
}
