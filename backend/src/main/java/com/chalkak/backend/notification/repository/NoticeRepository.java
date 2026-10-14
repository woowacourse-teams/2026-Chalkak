package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository {

    Notice saveAndFlush(Notice notice);

    Optional<Notice> findById(UUID noticeId);

    NoticeSlice findNotices(int page, int pageSize);

    List<DistributableNotice> findDistributableNotices(
            Instant now,
            DistributableNotice after,
            int limit
    );

    Optional<Notice> findDistributableForUpdate(UUID noticeId, Instant now);
}
