package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.IdTokenVerifier;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final List<IdTokenVerifier> idTokenVerifiers;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional(readOnly = true)
    public SocialLoginResult login(
            SocialProvider provider,
            String idToken
    ) {
        IdTokenVerifier verifier = getVerifier(provider);
        VerifiedSocialIdentity identity = verifier.verify(idToken);

        return socialAccountRepository.findByProviderAndSubject(
                        identity.provider(),
                        identity.subject())
                .map(this::toLoginSuccess)
                .orElseGet(SocialLoginResult::signUpRequired);
    }

    private IdTokenVerifier getVerifier(SocialProvider provider) {
        return idTokenVerifiers.stream()
                .filter(verifier -> verifier.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_ERROR,
                        "지원하지 않는 소셜 로그인 제공자입니다."));
    }

    private SocialLoginResult toLoginSuccess(SocialAccount socialAccount) {
        if (socialAccount.getUser().isDeleted()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "탈퇴한 회원은 로그인할 수 없습니다.");
        }
        return SocialLoginResult.loginSuccess(socialAccount.getUser().getId());
    }
}
