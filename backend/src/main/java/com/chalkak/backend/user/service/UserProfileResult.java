package com.chalkak.backend.user.service;

import com.chalkak.backend.user.domain.UserRole;

public record UserProfileResult(UserRole role, boolean artistTermsAgreed) {
}
