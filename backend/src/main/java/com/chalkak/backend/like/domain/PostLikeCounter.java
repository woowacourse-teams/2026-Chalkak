package com.chalkak.backend.like.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** JPQL 조회·조인 매핑용이다. 개수 변경은 저장소의 원자적 SQL로 처리한다. */
@Entity
@Table(name = "post_like_counts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLikeCounter {

    @Id
    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;
}
