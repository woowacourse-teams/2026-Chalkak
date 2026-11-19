package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JevPromptVersionJpaRepository extends JpaRepository<JevPromptVersion, UUID> {

    // 기존 프롬프트 버전과 같은 잠금 namespace를 사용하고 Jev 역할명으로 분리한다.
    @Query(value = "SELECT pg_advisory_xact_lock(2, hashtext(CAST(:key AS text)))", nativeQuery = true)
    void lockRole(@Param("key") String key);

    @Query("SELECT COALESCE(MAX(prompt.version), 0) FROM JevPromptVersion prompt WHERE prompt.role = :role")
    int findMaxVersion(@Param("role") JevPromptRole role);

    @Query("SELECT prompt.role FROM JevPromptVersion prompt WHERE prompt.id = :id")
    Optional<JevPromptRole> findRoleById(@Param("id") UUID id);

    Optional<JevPromptVersion> findByRoleAndActiveTrue(JevPromptRole role);

    @Query("SELECT COALESCE(MAX(prompt.disabledRevision), 0) FROM JevPromptVersion prompt")
    long findLatestDisabledRevision();

    Slice<JevPromptVersion> findByRole(JevPromptRole role, Pageable pageable);
}
