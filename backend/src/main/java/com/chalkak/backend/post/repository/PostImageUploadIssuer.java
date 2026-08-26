package com.chalkak.backend.post.repository;

import java.util.UUID;

public interface PostImageUploadIssuer {

    PresignedPostImageUpload issue(UUID uploadId);
}
