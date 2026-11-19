package com.chalkak.backend.jev.repository;

import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JevPromptVersionRepository {

    JevPromptVersion save(JevPromptVersion promptVersion);

    void lockRole(JevPromptRole role);

    int findMaxVersion(JevPromptRole role);

    Optional<JevPromptVersion> findById(UUID id);

    Optional<JevPromptRole> findRoleById(UUID id);

    Optional<JevPromptVersion> findActiveByRole(JevPromptRole role);

    VersionPage findByRole(
            JevPromptRole role,
            int page,
            int pageSize
    );

    void flush();

    record VersionPage(
            List<JevPromptVersion> items,
            int currentPage,
            int pageSize,
            boolean hasNext
    ) {
    }
}
