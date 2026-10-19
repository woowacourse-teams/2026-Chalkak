package com.chalkak.backend.common.logging;

import java.util.List;
import java.util.UUID;

/** 정상 피드 응답에서 확정한 조회 조건과 좋아요 수 조회 대상. 사용자 화면 노출이나 캐시 Hit 기록은 아니다. */
public record FeedQueryLog(
        String sort,
        int page,
        int pageSize,
        List<UUID> postIds) {

    public FeedQueryLog {
        postIds = List.copyOf(postIds);
    }
}
