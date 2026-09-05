package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ConsumedSignupTokenRepositoryImpl implements ConsumedSignupTokenRepository {

    private final ConsumedSignupTokenJpaRepository repository;

    /**
     * saveAndFlush로 이 자리에서 바로 INSERT를 실행해, 유니크 제약 위반을 여기서 잡는다.
     * flush 없이 save만 하면 실제 INSERT가 트랜잭션 커밋 시점까지 미뤄질 수 있어 이
     * 메서드 안에서 위반 여부를 알 수 없다.
     */
    @Override
    public boolean consumeIfAbsent(ConsumedSignupToken token) {
        try {
            repository.saveAndFlush(token);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }

    @Override
    public void deleteAllExpiredBefore(Instant now) {
        repository.deleteByExpiresAtBefore(now);
    }
}
