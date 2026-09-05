package com.chalkak.backend.user.service;

import java.util.List;
import java.util.UUID;

public interface SocialConnectionRevoker {

    List<SocialConnectionRevocationSnapshot> revokeAll(UUID userId);
}
