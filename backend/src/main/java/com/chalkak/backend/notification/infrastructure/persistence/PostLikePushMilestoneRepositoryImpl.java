package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.repository.PostLikePushMilestoneRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostLikePushMilestoneRepositoryImpl implements PostLikePushMilestoneRepository {

    private final PostLikePushMilestoneJpaRepository postLikePushMilestoneJpaRepository;

    @Override
    public PostLikePushMilestone save(PostLikePushMilestone milestone) {
        return postLikePushMilestoneJpaRepository.save(milestone);
    }

    @Override
    public Optional<Long> findMaxCountByPostId(UUID postId) {
        return postLikePushMilestoneJpaRepository.findMaxCountByPostId(postId);
    }
}
