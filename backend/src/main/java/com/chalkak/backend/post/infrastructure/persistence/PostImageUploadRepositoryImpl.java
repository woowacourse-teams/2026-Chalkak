package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostImageUploadRepositoryImpl implements PostImageUploadRepository {

    private final PostImageUploadJpaRepository postImageUploadJpaRepository;

    @Override
    public PostImageUpload save(PostImageUpload postImageUpload) {
        return postImageUploadJpaRepository.save(postImageUpload);
    }

    @Override
    public Optional<PostImageUpload> findByIdForUpdate(UUID uploadId) {
        return postImageUploadJpaRepository.findByIdForUpdate(uploadId);
    }

    @Override
    public Optional<PostImageUploadStatus> findStatusByIdAndUserId(UUID uploadId, UUID userId) {
        return postImageUploadJpaRepository.findStatusByIdAndUserId(uploadId, userId);
    }
}
