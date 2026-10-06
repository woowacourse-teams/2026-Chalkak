package com.chalkak.backend.notification.domain;

import com.chalkak.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "push_devices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "fcm_token", nullable = false, columnDefinition = "text")
    private String fcmToken;

    @Column(name = "fcm_token_hash", nullable = false, length = 64)
    private String fcmTokenHash;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PushDevice(
            User user,
            UUID sessionId,
            FcmToken token,
            Instant registeredAt
    ) {
        this.user = user;
        this.sessionId = sessionId;
        this.registeredAt = registeredAt;
        updateToken(token, registeredAt);
    }

    public void updateToken(FcmToken token, Instant updatedAt) {
        this.fcmToken = token.getValue();
        this.fcmTokenHash = token.getHash();
        this.updatedAt = updatedAt;
    }
}
