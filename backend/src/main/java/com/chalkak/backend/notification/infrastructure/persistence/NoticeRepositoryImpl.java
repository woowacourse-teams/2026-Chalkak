package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.DistributableNotice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NoticeSlice;
import java.time.Instant;
import java.util.List;
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

    private static final Sort LATEST_CREATED_FIRST = Sort.by(
            Sort.Direction.DESC,
            "createdAt",
            "id"
    );

    private final NoticeJpaRepository noticeJpaRepository;

    @Override
    public Notice saveAndFlush(Notice notice) {
        return noticeJpaRepository.saveAndFlush(notice);
    }

    @Override
    public Optional<Notice> findById(UUID noticeId) {
        return noticeJpaRepository.findById(noticeId);
    }

    @Override
    public Optional<Notice> findByIdForUpdate(UUID noticeId) {
        return noticeJpaRepository.findByIdForUpdate(noticeId);
    }

    @Override
    public NoticeSlice findNotices(int page, int pageSize) {
        Slice<Notice> slice = noticeJpaRepository.findSliceBy(
                PageRequest.of(
                        page - 1,
                        pageSize,
                        LATEST_CREATED_FIRST
                ));
        return new NoticeSlice(slice.getContent(), slice.hasNext());
    }

    @Override
    public List<DistributableNotice> findDistributableNotices(
            Instant now,
            DistributableNotice after,
            int limit
    ) {
        PageRequest firstPage = PageRequest.of(0, limit);
        if (after == null) {
            return noticeJpaRepository.findDistributableNotices(now, firstPage);
        }
        return noticeJpaRepository.findDistributableNoticesAfter(
                now,
                after.scheduledAt(),
                after.noticeId(),
                firstPage
        );
    }

    @Override
    public Optional<Notice> findDistributableForUpdate(UUID noticeId, Instant now) {
        return noticeJpaRepository.findDistributableForUpdate(noticeId, now);
    }
}
