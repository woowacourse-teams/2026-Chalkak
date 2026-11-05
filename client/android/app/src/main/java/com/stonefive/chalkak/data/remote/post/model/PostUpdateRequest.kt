package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

/**
 * 게시물 수정 요청. 서버는 제목·설명·인스타그램 동의를 한 번에 통째로 교체한다(POST-010).
 * 빠진 필드는 "없음"으로 덮어쓰므로, 고치지 않은 값도 현재 값을 다시 보내야 한다.
 * 인스타그램 동의는 상세 응답의 instagramConsent로 채운 수정 입력 값을 그대로 싣는다(이슈 #939).
 * 동의 안 함(false)·ID 없음(null)은 기본값이라 JSON에서 생략되고, 서버는 생략을 "동의 철회·ID 삭제"로 본다.
 */
@Serializable
data class PostUpdateRequest(
    val title: String?,
    // 생략하면 서버가 설명을 지우므로(POST-010) 비어 있어도 null로 항상 보낸다.
    val description: String?,
    val instagramUploadAgreed: Boolean = false,
    val instagramId: String? = null,
)
