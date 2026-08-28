package com.chalkak.backend.post.repository;

import java.util.UUID;

public interface PostImageStorage {

    boolean existsUploadedImage(UUID uploadId);

    String toStagingStorageKey(UUID uploadId);

    String toOriginalStorageKey(UUID uploadId);

    String toThumbnailStorageKey(UUID uploadId);

    void deleteImage(String storageKey);
}
