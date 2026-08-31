package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issue(UUID userId);

    IssuedAccessToken issue(UUID subjectId, AccessTokenScope scope);
}
