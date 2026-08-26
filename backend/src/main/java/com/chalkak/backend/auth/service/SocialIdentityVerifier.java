package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialIdentityVerifier {

    private final List<IdTokenVerifier> idTokenVerifiers;

    public VerifiedSocialIdentity verify(
            SocialProvider provider,
            String idToken
    ) {
        IdTokenVerifier verifier = getVerifier(provider);
        return verifier.verify(idToken);
    }

    private IdTokenVerifier getVerifier(SocialProvider provider) {
        return idTokenVerifiers.stream()
                .filter(verifier -> verifier.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "지원하지 않는 소셜 로그인 제공자입니다."));
    }
}
