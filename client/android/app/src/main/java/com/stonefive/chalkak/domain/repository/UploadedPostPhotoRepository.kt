package com.stonefive.chalkak.domain.repository

// 서버가 사진 처리를 끝내기 전(VALIDATING) 홈에 보여 줄, 방금 올린 사진. 게시물 하나만 둔다.
interface UploadedPostPhotoRepository {
    // 그 게시물의 보관 사진을 이미지 로더가 읽을 수 있는 파일 경로로 준다. 없으면 null.
    fun photoPath(postId: String): String?

    fun clear()
}
