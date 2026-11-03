package com.chalkak.backend.like.infrastructure.persistence;

import com.chalkak.backend.like.repository.PostLikeCounterRepository;
import com.chalkak.backend.like.repository.PostLikeCount;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PostLikeCounterRepositoryImpl implements PostLikeCounterRepository {

    private final PostLikeCounterJpaRepository postLikeCounterJpaRepository;

    @Override
    public long getCountByPostId(UUID postId) {
        return postLikeCounterJpaRepository.findCountByPostId(postId)
                .orElseThrow(() -> new DataIntegrityViolationException(
                        "좋아요 집계 행을 찾을 수 없습니다: " + postId));
    }

    @Override
    public long getLastMilestoneCountByPostId(UUID postId) {
        return postLikeCounterJpaRepository.findLastMilestoneCountByPostId(postId)
                .orElseThrow(() -> new DataIntegrityViolationException(
                        "좋아요 집계 행을 찾을 수 없습니다: " + postId));
    }

    @Override
    public void updateLastMilestoneCount(UUID postId, long milestoneCount) {
        validateAffectedRowCount(
                postLikeCounterJpaRepository.updateLastMilestoneCount(postId, milestoneCount),
                postId
        );
    }

    @Override
    public List<PostLikeCount> getCountsByPostIds(List<UUID> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        List<PostLikeCount> counts = postLikeCounterJpaRepository.findCountsByPostIds(postIds);
        if (counts.size() != new HashSet<>(postIds).size()) {
            throw new DataIntegrityViolationException("조회 대상 게시물의 좋아요 집계 행이 누락됐습니다.");
        }
        return counts;
    }

    @Override
    public void create(UUID postId) {
        validateAffectedRowCount(postLikeCounterJpaRepository.create(postId), postId);
    }

    @Override
    public void increment(UUID postId) {
        validateAffectedRowCount(postLikeCounterJpaRepository.increment(postId), postId);
    }

    @Override
    public void decrement(UUID postId) {
        validateAffectedRowCount(postLikeCounterJpaRepository.decrement(postId), postId);
    }

    @Override
    public void reset(UUID postId) {
        validateAffectedRowCount(postLikeCounterJpaRepository.reset(postId), postId);
    }

    private void validateAffectedRowCount(int affectedRows, UUID postId) {
        if (affectedRows != 1) {
            // 집계가 누락된 상태에서 관계만 커밋하지 않고 전체 트랜잭션을 실패시킨다.
            throw new DataIntegrityViolationException("좋아요 집계 행을 갱신하지 못했습니다: " + postId);
        }
    }
}
