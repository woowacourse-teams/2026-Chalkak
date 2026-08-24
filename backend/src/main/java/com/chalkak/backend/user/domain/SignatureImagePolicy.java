package com.chalkak.backend.user.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.List;


public record SignatureImagePolicy(long maxBytes, List<String> allowedContentTypes) {

    public void validate(StoredImageMetadata image) {
        if (image.contentType() == null || !allowedContentTypes.contains(image.contentType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "사용할 수 없는 사인 이미지입니다.");
        }
        if (image.byteSize() > maxBytes) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "사용할 수 없는 사인 이미지입니다.");
        }
    }
}