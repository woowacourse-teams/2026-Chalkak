package com.chalkak.backend.user.repository;

import java.util.UUID;

public interface SignatureImageUploadIssuer {

    SignatureImageUpload issue(UUID uploadId);

    SignatureProcessingImageUpload issueProcessingUpload(UUID uploadId);
}
