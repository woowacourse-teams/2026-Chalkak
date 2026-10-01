package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issueForSession(UUID userId, UUID sessionId);

    IssuedAccessToken issue(UUID subjectId, AccessTokenScope scope);
}
