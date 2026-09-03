package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleAuthorizationService {

    private final SocialAccountRepository socialAccountRepository;
    private final AppleAuthorizationRepository appleAuthorizationRepository;

    @Transactional
    public Optional<UUID> saveForExistingAccount(
            String subjectHmac,
            String clientId,
            String encryptedRefreshToken
    ) {
        Optional<SocialAccount> socialAccount = socialAccountRepository
                .findByProviderAndSubjectHmacForUpdate(
                        SocialProvider.APPLE,
                        subjectHmac);
        if (socialAccount.isEmpty()) {
            return Optional.empty();
        }

        User user = socialAccount.get().getUser();
        validateNotWithdrawnBannedAccount(user);
        if (user.isDeleted()) {
            return Optional.empty();
        }

        saveOrUpdate(
                socialAccount.get(),
                clientId,
                encryptedRefreshToken);
        return Optional.of(user.getId());
    }

    /**
     * 탈퇴 시 Apple에 폐기 요청을 보내기 위한 조회다. 외부 호출을 트랜잭션 밖에서 하려고
     * 조회만 짧게 끊어 담당한다.
     */
    @Transactional(readOnly = true)
    public List<String> findEncryptedRefreshTokens(UUID userId) {
        return socialAccountRepository.findByUserId(userId)
                .map(socialAccount -> appleAuthorizationRepository
                        .findAllBySocialAccountId(socialAccount.getId()))
                .orElseGet(List::of)
                .stream()
                .map(AppleAuthorization::getEncryptedRefreshToken)
                .toList();
    }

    private void validateNotWithdrawnBannedAccount(User user) {
        if (user.isDeleted() && user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "탈퇴한 차단 소셜 계정입니다.");
        }
    }

    private void saveOrUpdate(
            SocialAccount socialAccount,
            String clientId,
            String encryptedRefreshToken
    ) {
        Optional<AppleAuthorization> existing = appleAuthorizationRepository
                .findBySocialAccountIdAndClientIdForUpdate(
                        socialAccount.getId(),
                        clientId);
        if (existing.isPresent()) {
            existing.get().updateEncryptedRefreshToken(encryptedRefreshToken);
            return;
        }
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                clientId,
                encryptedRefreshToken));
    }
}
