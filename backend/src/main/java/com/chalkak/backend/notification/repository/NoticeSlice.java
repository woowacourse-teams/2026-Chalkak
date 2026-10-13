package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.Notice;
import java.util.List;

public record NoticeSlice(List<Notice> notices, boolean hasNext) {
}
