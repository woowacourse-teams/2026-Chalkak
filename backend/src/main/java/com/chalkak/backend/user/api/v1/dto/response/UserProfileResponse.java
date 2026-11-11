package com.chalkak.backend.user.api.v1.dto.response;

import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.service.UserProfileResult;

public record UserProfileResponse(UserRole role, boolean artistTermsAgreed) {

    public static UserProfileResponse from(UserProfileResult result) {
        return new UserProfileResponse(result.role(), result.artistTermsAgreed());
    }
}
