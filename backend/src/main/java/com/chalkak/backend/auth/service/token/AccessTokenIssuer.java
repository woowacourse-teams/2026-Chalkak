package com.chalkak.backend.auth.service.token;

import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.domain.token.IssuedAccessToken;
import java.util.UUID;

public interface AccessTokenIssuer {

    IssuedAccessToken issueForSession(UUID userId, UUID sessionId);

    IssuedAccessToken issue(UUID subjectId, AccessTokenScope scope);
}
