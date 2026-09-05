package com.chalkak.backend.user.service;

import java.util.List;
import java.util.UUID;

public interface SocialConnectionRevocationStore {

    void deleteAllIfUnchanged(
            UUID socialAccountId,
            List<SocialConnectionRevocationSnapshot> revokedConnections
    );
}
