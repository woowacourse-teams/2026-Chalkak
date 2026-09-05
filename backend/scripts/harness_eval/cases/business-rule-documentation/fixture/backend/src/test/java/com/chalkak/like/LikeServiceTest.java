package com.chalkak.like;

final class LikeServiceTest {

    void bannedMemberCannotAddLike() {
        // BANNED 회원의 add 요청은 예외가 발생해야 한다.
    }

    void bannedMemberCanCancelExistingLike() {
        // BANNED 회원의 cancel 요청은 기존 좋아요를 삭제해야 한다.
    }
}
