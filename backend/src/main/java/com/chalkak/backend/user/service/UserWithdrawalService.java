package com.chalkak.backend.user.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 탈퇴 절차를 조립한다. 소셜 연결 폐기는 외부 호출을 포함할 수 있어 DB 트랜잭션이 응답을 기다리며
 * 커넥션을 물고 있지 않도록, 이 클래스에는 트랜잭션을 걸지 않고 조회와 탈퇴만 각각 짧은
 * 트랜잭션을 가진 다른 빈에 위임한다.
 *
 * <p>폐기에 실패하면 예외가 그대로 올라가 탈퇴가 진행되지 않는다. DB가 그대로 남아 사용자가
 * 다시 시도할 수 있다.
 *
 * <p>반대로 폐기가 끝난 뒤 탈퇴가 실패하면, 회원은 남고 소셜 연결만 끊긴 상태가 된다. 외부
 * 폐기는 되돌릴 수 없으므로 이 상태는 재시도로만 수렴한다. Apple은 기존 회원 로그인에서
 * authorizationCode를 교환하지 않으므로 그 사이에도 로그인은 계속되고, 다시 탈퇴를 요청하면
 * 이미 폐기된 연결에 대한 재폐기가 성공으로 처리되어 그대로 진행된다.
 *
 * <p>폐기한 연결의 스냅샷을 {@link UserService#withdraw}에 넘겨, 폐기와 삭제 사이에 인증
 * 상태가 바뀌면 탈퇴를 중단하게 한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final SocialConnectionRevoker socialConnectionRevoker;
    private final UserService userService;

    public void withdraw(UUID userId) {
        List<SocialConnectionRevocationSnapshot> snapshots =
                socialConnectionRevoker.revokeAll(userId);

        userService.withdraw(userId, snapshots);
    }
}
