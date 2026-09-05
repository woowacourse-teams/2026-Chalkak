package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.service.SocialConnectionRevocationSnapshot;
import com.chalkak.backend.user.service.SocialConnectionRevocationStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleSocialConnectionRevocationStore
        implements SocialConnectionRevocationStore {

    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final AppleAuthorizationFingerprintEncoder fingerprintEncoder;

    @Override
    public void deleteAllIfUnchanged(
            UUID socialAccountId,
            List<SocialConnectionRevocationSnapshot> revokedConnections
    ) {
        List<AppleAuthorization> current = appleAuthorizationRepository
                .findAllBySocialAccountId(socialAccountId);
        validateUnchanged(current, revokedConnections);
        appleAuthorizationRepository.deleteAllBySocialAccountId(socialAccountId);
    }

    private void validateUnchanged(
            List<AppleAuthorization> current,
            List<SocialConnectionRevocationSnapshot> revokedConnections
    ) {
        if (matchesRevoked(current, revokedConnections)) {
            return;
        }
        throw new BusinessException(
                ErrorCode.RESOURCE_STATE_CHANGED,
                "탈퇴 처리 중 Apple 인증 정보가 변경되었습니다. 다시 시도해 주세요.");
    }

    private boolean matchesRevoked(
            List<AppleAuthorization> current,
            List<SocialConnectionRevocationSnapshot> revokedConnections
    ) {
        if (current.size() != revokedConnections.size()) {
            return false;
        }
        Set<SocialConnectionRevocationSnapshot> currentSnapshots = current.stream()
                .map(this::toSocialConnectionSnapshot)
                .collect(Collectors.toSet());
        return currentSnapshots.containsAll(revokedConnections);
    }

    private SocialConnectionRevocationSnapshot toSocialConnectionSnapshot(
            AppleAuthorization authorization
    ) {
        return new SocialConnectionRevocationSnapshot(
                authorization.getId(),
                fingerprintEncoder.encode(
                        authorization.getEncryptedRefreshToken()));
    }
}
