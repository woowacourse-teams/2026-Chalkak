package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleAuthorizationService {

    private final SocialAccountRepository socialAccountRepository;
    private final AppleAuthorizationRepository appleAuthorizationRepository;

    /**
     * 탈퇴 시 Apple에 폐기 요청을 보내기 위한 조회다. 외부 호출을 트랜잭션 밖에서 하려고
     * 조회만 짧게 끊어 담당한다. id까지 담는 것은 탈퇴 트랜잭션이 삭제 직전 이 스냅샷과
     * DB의 현재 상태를 대조할 수 있어야 하기 때문이다.
     */
    @Transactional(readOnly = true)
    public List<AppleAuthorizationSnapshot> findAuthorizationSnapshots(UUID userId) {
        return socialAccountRepository.findByUserId(userId)
                .map(socialAccount -> appleAuthorizationRepository
                        .findAllBySocialAccountId(socialAccount.getId()))
                .orElseGet(List::of)
                .stream()
                .map(this::toSnapshot)
                .toList();
    }

    private AppleAuthorizationSnapshot toSnapshot(AppleAuthorization authorization) {
        return new AppleAuthorizationSnapshot(
                authorization.getId(),
                authorization.getEncryptedRefreshToken());
    }
}
