package com.chalkak.backend.post.repository;

import com.chalkak.backend.post.domain.PostImageUpload;
import java.util.Optional;
import java.util.UUID;

public interface PostImageUploadRepository {

    PostImageUpload save(PostImageUpload postImageUpload);

    Optional<PostImageUpload> findByIdForUpdate(UUID uploadId);
}
