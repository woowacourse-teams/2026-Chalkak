package com.chalkak.backend.appversion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "app_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersion {

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private AppPlatform platform;

    @Column(name = "minimum_supported_version", nullable = false, columnDefinition = "text")
    private String minimumSupportedVersion;

    @Column(name = "store_url", nullable = false, columnDefinition = "text")
    private String storeUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
