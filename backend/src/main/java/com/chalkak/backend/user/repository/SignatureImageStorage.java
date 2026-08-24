package com.chalkak.backend.user.repository;

import com.chalkak.backend.user.domain.StoredImageMetadata;
import java.util.Optional;
import java.util.UUID;

public interface SignatureImageStorage {

    Optional<StoredImageMetadata> findUploadedImage(UUID uploadId);

    String toOriginalStorageKey(UUID uploadId);

    String toImageUrl(String storageKey);
}
