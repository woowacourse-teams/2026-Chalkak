package com.chalkak.backend.post.repository;

import java.time.Duration;
import java.util.UUID;

public interface PostImageUploadIssuer {

    PresignedPostImageUpload issue(UUID uploadId);

    PostProcessingImageUpload issueProcessingUpload(UUID uploadId);

    Duration processingUploadUrlValidity();
}
