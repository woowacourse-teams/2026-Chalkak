package com.chalkak.backend.user.repository;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import java.util.Optional;
import java.util.UUID;

public interface SignatureImageStorage {

    Optional<StoredImageMetadata> findUploadedImage(UUID uploadId);

    String toStagingStorageKey(UUID uploadId);

    SignatureStorageKeys toStorageKeys(UUID uploadId);

    boolean isProcessingCompleted(UUID uploadId);

    String toImageUrl(String storageKey);
}
