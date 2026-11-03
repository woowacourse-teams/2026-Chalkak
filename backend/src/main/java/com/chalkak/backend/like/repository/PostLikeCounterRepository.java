package com.chalkak.backend.like.repository;

import java.util.List;
import java.util.UUID;

public interface PostLikeCounterRepository {

    long getCountByPostId(UUID postId);

    long getLastMilestoneCountByPostId(UUID postId);

    void updateLastMilestoneCount(UUID postId, long milestoneCount);

    List<PostLikeCount> getCountsByPostIds(List<UUID> postIds);

    void create(UUID postId);

    void increment(UUID postId);

    void decrement(UUID postId);

    void reset(UUID postId);
}
