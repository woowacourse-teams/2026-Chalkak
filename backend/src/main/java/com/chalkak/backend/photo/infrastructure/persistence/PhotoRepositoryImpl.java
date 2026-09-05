package com.chalkak.backend.photo.infrastructure.persistence;

import com.chalkak.backend.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PhotoRepositoryImpl implements PhotoRepository {

    private final PhotoJpaRepository photoJpaRepository;

    @Override
    public boolean existsByOriginalStorageKey(String originalStorageKey) {
        return photoJpaRepository.existsByOriginalStorageKey(originalStorageKey);
    }
}
