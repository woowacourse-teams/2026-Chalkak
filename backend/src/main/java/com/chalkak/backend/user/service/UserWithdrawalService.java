package com.chalkak.backend.user.service;

import com.chalkak.backend.auth.service.AppleAuthorizationService;
import com.chalkak.backend.auth.service.AppleAuthorizationSnapshot;
import com.chalkak.backend.auth.service.AppleAuthorizationCipher;
import com.chalkak.backend.auth.service.AppleTokenClient;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 절차를 조립한다. Apple 폐기 요청은 외부 HTTP 호출이라 DB 트랜잭션이 응답을 기다리며
 * 커넥션을 물고 있지 않도록, 이 클래스에는 트랜잭션을 걸지 않고 조회와 탈퇴만 각각 짧은
 * 트랜잭션을 가진 다른 빈에 위임한다. 같은 빈 안에서 나눠도 자기 호출은 프록시를 타지 않아
 * 트랜잭션 경계가 생기지 않는다.
 *
 * <p>폐기에 실패하면 예외가 그대로 올라가 탈퇴가 진행되지 않는다. DB가 그대로 남아 사용자가
 * 다시 시도할 수 있고, Apple은 이미 폐기된 RT를 다시 폐기해도 200으로 응답하므로 재시도가
 * 안전하다.
 *
 * <p>조회 시점에 읽은 스냅샷을 그대로 {@link UserService#withdraw}에 넘겨, 폐기와 삭제
 * 사이에 다른 로그인이 끼어들어도 그 사용자의 인증 정보만은 지워지지 않게 한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final AppleAuthorizationService appleAuthorizationService;
    private final AppleAuthorizationCipher authorizationCipher;
    private final AppleTokenClient appleTokenClient;
    private final UserService userService;

    public void withdraw(UUID userId) {
        List<AppleAuthorizationSnapshot> snapshots =
                appleAuthorizationService.findAuthorizationSnapshots(userId);
        revokeAuthorizations(snapshots);

        userService.withdraw(userId, snapshots);
    }

    private void revokeAuthorizations(List<AppleAuthorizationSnapshot> snapshots) {
        for (AppleAuthorizationSnapshot snapshot : snapshots) {
            appleTokenClient.revokeRefreshToken(
                    authorizationCipher.decrypt(snapshot.encryptedRefreshToken()),
                    snapshot.clientId());
        }
    }
}
