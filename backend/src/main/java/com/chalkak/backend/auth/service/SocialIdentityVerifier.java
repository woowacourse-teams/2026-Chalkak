package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class SocialIdentityVerifier {

    private final Map<SocialProvider, IdTokenVerifier> verifiersByProvider;

    public SocialIdentityVerifier(List<IdTokenVerifier> idTokenVerifiers) {
        this.verifiersByProvider = idTokenVerifiers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        IdTokenVerifier::getProvider,
                        Function.identity()));
    }

    public VerifiedSocialIdentity verify(
            SocialProvider provider,
            String idToken
    ) {
        IdTokenVerifier verifier = getVerifier(provider);
        return verifier.verify(idToken);
    }

    private IdTokenVerifier getVerifier(SocialProvider provider) {
        IdTokenVerifier verifier = verifiersByProvider.get(provider);
        if (verifier == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "지원하지 않는 소셜 로그인 제공자입니다.");
        }
        return verifier;
    }
}
