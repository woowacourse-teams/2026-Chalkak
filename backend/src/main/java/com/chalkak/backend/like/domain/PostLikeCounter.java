package com.chalkak.backend.like.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_like_counts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostLikeCounter {

    @Id
    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;
}
