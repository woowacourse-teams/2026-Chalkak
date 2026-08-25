package com.chalkak.backend.user.repository;

import com.chalkak.backend.user.domain.SignatureImageUpload;
import java.util.UUID;

public interface SignatureImageUploadIssuer {

    SignatureImageUpload issue(UUID uploadId);
}
