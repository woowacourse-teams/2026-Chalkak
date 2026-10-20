package com.chalkak.backend.aireview.repository;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import java.util.Optional;
import java.util.UUID;

public interface AiPromptVersionRepository {

    AiPromptVersion save(AiPromptVersion promptVersion);

    /**
     * 용도 하나의 버전 번호 부여를 트랜잭션이 끝날 때까지 직렬화한다. 해제 호출은 없고 커밋이나 롤백이 푼다.
     */
    void lockType(AiPromptType type);

    /**
     * 용도의 가장 큰 버전 번호. 아직 버전이 없으면 0이다.
     */
    int findMaxVersion(AiPromptType type);

    /**
     * 용도의 가장 큰 버전. 출력 스키마나 모델은 거르지 않으며, 쓸 수 있는지는 호출하는 쪽이 판단한다.
     */
    Optional<AiPromptVersion> findLatestByType(AiPromptType type);

    Optional<AiPromptVersion> findById(UUID promptVersionId);

    /**
     * 용도의 버전을 큰 번호부터 페이지 단위로 조회한다. page는 1부터 시작한다.
     */
    AiPromptVersionPage findByType(AiPromptType type, int page, int pageSize);
}
