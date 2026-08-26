package com.chalkak.backend.auth.infrastructure.infra;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.service.SocialSignupTokenIssuer;
import com.chalkak.backend.auth.service.SocialSignupTokenVerifier;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

public class JwtSocialSignupTokenProvider implements
        SocialSignupTokenIssuer,
        SocialSignupTokenVerifier {

    private static final int SUBJECT_MAX_LENGTH = 255;
    private static final String PURPOSE = "SOCIAL_SIGNUP";
    private static final String PROVIDER_CLAIM = "provider";
    private static final String PURPOSE_CLAIM = "purpose";
    private static final String UPLOAD_ID_CLAIM = "uploadId";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final SocialSignupTokenProperties properties;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtSocialSignupTokenProvider(
            SocialSignupTokenProperties properties
    ) {
        this(properties, Clock.systemUTC());
    }

    JwtSocialSignupTokenProvider(
            SocialSignupTokenProperties properties,
            Clock clock
    ) {
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
    public IssuedSocialSignupToken issue(
            VerifiedSocialIdentity identity,
            UUID uploadId
    ) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(identity.subject())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(PURPOSE_CLAIM, PURPOSE)
                .claim(PROVIDER_CLAIM, identity.provider().name())
                .claim(UPLOAD_ID_CLAIM, uploadId.toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new IssuedSocialSignupToken(value);
    }

    @Override
    public VerifiedSocialSignupToken verify(String signupToken) {
        try {
            Jwt jwt = jwtDecoder.decode(signupToken);
            return new VerifiedSocialSignupToken(
                    getProvider(jwt),
                    getSubject(jwt),
                    getUploadId(jwt));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "유효하지 않은 회원가입 정보입니다.");
        }
    }

    private JwtDecoder createJwtDecoder(SecretKey secretKey, Clock clock) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(CLOCK_SKEW);
        timestampValidator.setClock(clock);
        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.issuer()),
                this::validateAudience,
                this::validatePurpose,
                this::validateRequiredTimestamps));
        return jwtDecoder;
    }

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

    private OAuth2TokenValidatorResult validateRequiredTimestamps(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        if (issuedAt == null || expiresAt == null) {
            return invalidTokenResult();
        }
        if (issuedAt.isAfter(clock.instant().plus(CLOCK_SKEW))) {
            return invalidTokenResult();
        }
        if (!expiresAt.isAfter(issuedAt)) {
            return invalidTokenResult();
        }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalidTokenResult() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "Invalid social signup token",
                null));
    }

    private SocialProvider getProvider(Jwt jwt) {
        String provider = jwt.getClaimAsString(PROVIDER_CLAIM);
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Missing social provider");
        }
        return SocialProvider.valueOf(provider);
    }

    private String getSubject(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null
                || subject.isBlank()
                || subject.length() > SUBJECT_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid social subject");
        }
        return subject;
    }

    private UUID getUploadId(Jwt jwt) {
        String uploadId = jwt.getClaimAsString(UPLOAD_ID_CLAIM);
        if (uploadId == null || uploadId.isBlank()) {
            throw new IllegalArgumentException("Missing signature upload ID");
        }
        return UUID.fromString(uploadId);
    }
}
