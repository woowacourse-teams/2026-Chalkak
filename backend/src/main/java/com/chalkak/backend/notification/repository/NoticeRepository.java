package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notice;

public interface NoticeRepository {

    Notice saveAndFlush(Notice notice);
}
