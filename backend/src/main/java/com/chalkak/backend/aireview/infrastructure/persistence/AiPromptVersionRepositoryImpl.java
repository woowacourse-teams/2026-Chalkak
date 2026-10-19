package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AiPromptVersionRepositoryImpl implements AiPromptVersionRepository {

    private static final String LOCK_KEY_PREFIX = "ai_prompt_versions:";

    private final AiPromptVersionJpaRepository jpaRepository;

    @Override
    public AiPromptVersion save(AiPromptVersion promptVersion) {
        return jpaRepository.save(promptVersion);
    }

    @Override
    public void lockType(AiPromptType type) {
        jpaRepository.lockByKey(LOCK_KEY_PREFIX + type.name());
    }

    @Override
    public int findMaxVersion(AiPromptType type) {
        return jpaRepository.findMaxVersionByType(type);
    }
}
