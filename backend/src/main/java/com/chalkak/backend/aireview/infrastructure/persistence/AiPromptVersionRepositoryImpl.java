package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.repository.AiPromptVersionPage;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
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

    @Override
    public Optional<AiPromptVersion> findById(UUID promptVersionId) {
        return jpaRepository.findById(promptVersionId);
    }

    @Override
    public AiPromptVersionPage findByType(AiPromptType type, int page, int pageSize) {
        PageRequest pageable = PageRequest.of(page - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "version"));
        Slice<AiPromptVersion> result = jpaRepository.findByType(type, pageable);
        return new AiPromptVersionPage(result.getContent(), page, pageSize, result.hasNext());
    }
}
