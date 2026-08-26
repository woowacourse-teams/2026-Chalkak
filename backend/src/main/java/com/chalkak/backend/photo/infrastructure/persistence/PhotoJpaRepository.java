package com.chalkak.backend.photo.infrastructure.persistence;

import com.chalkak.backend.photo.domain.Photo;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoJpaRepository extends JpaRepository<Photo, UUID> {

    boolean existsByOriginalStorageKey(String originalStorageKey);
}
