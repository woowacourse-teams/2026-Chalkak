package com.chalkak.like;

final class LikeService {
    private final LikeRepository likeRepository;

    LikeService(LikeRepository likeRepository) {
        this.likeRepository = likeRepository;
    }

    void add(long memberId, long postId, MemberStatus status) {
        if (status == MemberStatus.BANNED) {
            throw new IllegalStateException("정지 회원은 좋아요를 등록할 수 없습니다.");
        }
        likeRepository.save(memberId, postId);
    }

    void cancel(long memberId, long postId, MemberStatus status) {
        likeRepository.delete(memberId, postId);
    }

    enum MemberStatus {
        ACTIVE,
        BANNED
    }

    interface LikeRepository {
        void save(long memberId, long postId);

        void delete(long memberId, long postId);
    }
}
