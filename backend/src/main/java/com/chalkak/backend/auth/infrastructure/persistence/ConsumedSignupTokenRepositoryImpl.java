package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsumedSignupTokenRepositoryImpl implements ConsumedSignupTokenRepository {

    private final ConsumedSignupTokenJpaRepository repository;

    /**
     * 이미 있는 jti는 {@code ON CONFLICT DO NOTHING}으로 건너뛰고, 실제로 넣은 행 수로 최초 사용 여부를 판정한다.
     * 기본키 제약 위반 예외로 판정하면 PostgreSQL이 그 트랜잭션의 이후 쿼리를 모두 거부하므로 예외 없이 끝나는 문장을 쓴다.
     *
     * <p>
     * 같은 jti를 동시에 넣으면 뒤의 문장은 앞 트랜잭션이 끝날 때까지 기다린다. 앞이 커밋하면 0행, 롤백하면 1행이 되어 한 트랜잭션만
     * 성공한다.
     */
    @Override
    public boolean consumeIfAbsent(ConsumedSignupToken token) {
        return repository.createIfAbsent(token.getJti(), token.getExpiresAt()) == 1;
    }

    @Override
    public void deleteAllExpiredBefore(Instant now) {
        repository.deleteByExpiresAtBefore(now);
    }
}
