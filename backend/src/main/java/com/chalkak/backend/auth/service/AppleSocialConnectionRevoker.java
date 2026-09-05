package com.chalkak.backend.auth.service;

import com.chalkak.backend.user.service.SocialConnectionRevocationSnapshot;
import com.chalkak.backend.user.service.SocialConnectionRevoker;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleSocialConnectionRevoker implements SocialConnectionRevoker {

    private final AppleAuthorizationService appleAuthorizationService;
    private final AppleAuthorizationCipher authorizationCipher;
    private final AppleAuthorizationFingerprintEncoder fingerprintEncoder;
    private final AppleTokenClient appleTokenClient;

    /**
     * DB 조회 트랜잭션은 별도 빈에 위임하고 Apple HTTP 호출은 트랜잭션 밖에서 실행한다.
     * Apple은 이미 폐기된 RT의 재폐기도 성공으로 처리하므로 중간 실패 후 재시도할 수 있다.
     */
    @Override
    public List<SocialConnectionRevocationSnapshot> revokeAll(UUID userId) {
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(userId);
        snapshots.forEach(this::revoke);
        return snapshots.stream()
                .map(this::toSocialConnectionSnapshot)
                .toList();
    }

    private void revoke(AppleAuthorizationSnapshot snapshot) {
        String refreshToken = authorizationCipher.decrypt(
                snapshot.encryptedRefreshToken());
        appleTokenClient.revokeRefreshToken(refreshToken);
    }

    private SocialConnectionRevocationSnapshot toSocialConnectionSnapshot(
            AppleAuthorizationSnapshot snapshot
    ) {
        return new SocialConnectionRevocationSnapshot(
                snapshot.id(),
                fingerprintEncoder.encode(snapshot.encryptedRefreshToken()));
    }
}
