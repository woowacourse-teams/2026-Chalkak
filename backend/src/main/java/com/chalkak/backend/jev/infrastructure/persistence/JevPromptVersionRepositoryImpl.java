package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JevPromptVersionRepositoryImpl implements JevPromptVersionRepository {

    private final JevPromptVersionJpaRepository jpaRepository;

    @Override
    public JevPromptVersion save(JevPromptVersion promptVersion) {
        return jpaRepository.saveAndFlush(promptVersion);
    }

    @Override
    public void lockRole(JevPromptRole role) {
        jpaRepository.lockRole("ai_prompt_versions:" + role.name());
    }

    @Override
    public int findMaxVersion(JevPromptRole role) {
        return jpaRepository.findMaxVersion(role);
    }

    @Override
    public Optional<JevPromptVersion> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<JevPromptRole> findRoleById(UUID id) {
        return jpaRepository.findRoleById(id);
    }

    @Override
    public Optional<JevPromptVersion> findActiveByRole(JevPromptRole role) {
        return jpaRepository.findByRoleAndActiveTrue(role);
    }

    @Override
    public VersionPage findByRole(
            JevPromptRole role,
            int page,
            int pageSize
    ) {
        var result = jpaRepository.findByRole(role, PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Direction.DESC, "version")
        ));
        return new VersionPage(
                result.getContent(),
                page,
                pageSize,
                result.hasNext()
        );
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }
}
