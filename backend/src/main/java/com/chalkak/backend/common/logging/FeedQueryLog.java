package com.chalkak.backend.common.logging;

/** 정상 피드 응답에서 확정한 조회 조건. 실제 게시물 재조회 간격이나 캐시 Hit 기록은 아니다. */
public record FeedQueryLog(
        String sort,
        int page,
        int pageSize) {
}
