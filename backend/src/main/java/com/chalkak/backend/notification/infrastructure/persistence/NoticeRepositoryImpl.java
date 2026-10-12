package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NoticeSlice;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NoticeRepositoryImpl implements NoticeRepository {

    private static final Sort LATEST_CREATED_FIRST = Sort.by(Sort.Direction.DESC, "createdAt",
            "id");

    private final NoticeJpaRepository jpaRepository;

    @Override
    public Notice saveAndFlush(Notice notice) {
        return jpaRepository.saveAndFlush(notice);
    }

    @Override
    public Optional<Notice> findById(UUID noticeId) {
        return jpaRepository.findById(noticeId);
    }

    @Override
    public NoticeSlice findNotices(int page, int pageSize) {
        Slice<Notice> slice = jpaRepository.findSliceBy(
                PageRequest.of(page - 1, pageSize, LATEST_CREATED_FIRST));
        return new NoticeSlice(slice.getContent(), slice.hasNext());
    }
}
