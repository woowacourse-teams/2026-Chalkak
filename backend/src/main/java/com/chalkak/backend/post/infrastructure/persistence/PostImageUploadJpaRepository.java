package com.chalkak.backend.post.infrastructure.persistence;

import com.chalkak.backend.post.domain.PostImageUpload;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostImageUploadJpaRepository extends JpaRepository<PostImageUpload, UUID> {
}
