package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepository {

    private final NoticeJpaRepository jpaRepository;

    @Override
    public Notice saveAndFlush(Notice notice) {
        return jpaRepository.saveAndFlush(notice);
    }
}
