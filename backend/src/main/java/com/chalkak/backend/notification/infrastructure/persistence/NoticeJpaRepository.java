package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeJpaRepository extends JpaRepository<Notice, UUID> {
}
