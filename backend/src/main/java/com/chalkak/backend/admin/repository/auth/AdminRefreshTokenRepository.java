package com.chalkak.backend.admin.repository.auth;

import com.chalkak.backend.admin.domain.AdminRefreshToken;
import com.chalkak.backend.auth.repository.RefreshTokenRepository;

public interface AdminRefreshTokenRepository extends RefreshTokenRepository<AdminRefreshToken> {
}
