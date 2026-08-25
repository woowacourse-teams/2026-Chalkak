package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageUploadRepository;
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
}
