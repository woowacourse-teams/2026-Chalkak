package com.chalkak.backend.jev.repository;

import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JevJobRepository {

    void lockClassificationKey(String key);

    Optional<JevTopicCategory> findCategoryByKey(String key);

    Optional<JevTopicCategory> findCategory(UUID id);

    Optional<JevTopicCategory> findCategoryForUpdate(UUID id);

    JevTopicCategory saveCategory(JevTopicCategory category);

    JevModerationRun saveRun(JevModerationRun run);

    Optional<JevModerationRun> findRun(UUID id);

    Optional<JevModerationRun> findRunForUpdate(UUID id);

    List<UUID> findDueRunIds(Instant now);
}
