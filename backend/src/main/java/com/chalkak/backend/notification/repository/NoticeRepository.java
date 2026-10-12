package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notice;
import java.util.Optional;
import java.util.UUID;

public interface NoticeRepository {

    Notice saveAndFlush(Notice notice);

    Optional<Notice> findById(UUID noticeId);

    NoticeSlice findNotices(int page, int pageSize);
}
