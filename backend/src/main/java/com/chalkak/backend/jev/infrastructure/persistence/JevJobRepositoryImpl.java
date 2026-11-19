package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.jev.repository.JevJobTarget;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JevJobRepositoryImpl implements JevJobRepository {

    private final JevModerationRunJpaRepository runs;
    private final JevTopicCategoryJpaRepository categories;

    @Override
    public void lockClassificationKey(String key) {
        categories.lockClassificationKey(key);
    }

    @Override
    public Optional<JevTopicCategory> findCategoryByKey(String key) {
        return categories.findByClassificationKey(key);
    }

    @Override
    public Optional<JevTopicCategory> findCategory(UUID id) {
        return categories.findById(id);
    }

    @Override
    public Optional<JevTopicCategory> findCategoryForUpdate(UUID id) {
        return categories.findForUpdate(id);
    }

    @Override
    public JevTopicCategory saveCategory(JevTopicCategory category) {
        return categories.saveAndFlush(category);
    }

    @Override
    public JevModerationRun saveRun(JevModerationRun run) {
        return runs.saveAndFlush(run);
    }

    @Override
    public Optional<JevModerationRun> findRun(UUID id) {
        return runs.findById(id);
    }

    @Override
    public Optional<JevModerationRun> findRunForUpdate(UUID id) {
        return runs.findForUpdate(id);
    }

    @Override
    public Optional<JevJobTarget> findTarget(UUID id) {
        return runs.findTarget(id);
    }

    @Override
    public Optional<JevModerationRun> findProductionRunByPostId(UUID postId) {
        return runs.findByPostIdAndPreviewFalse(postId);
    }

    @Override
    public List<UUID> findUnappliedRunIds() {
        return runs.findUnappliedIds();
    }

    @Override
    public List<UUID> findDueRunIds(Instant now) {
        return runs.findDueIds(now);
    }

    @Override
    public List<UUID> findDueSlackIds(Instant now) {
        return runs.findDueSlackIds(now);
    }
}
