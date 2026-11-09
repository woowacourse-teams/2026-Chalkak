package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostImageUploadResponse(
    val uploadId: String,
    val uploadUrl: String,
    val expiresInSeconds: Long,
    val contentType: String,
    val maxBytes: Long,
)

@Serializable
data class PostCreateRequest(
    val topicId: String,
    val photoUploadId: String,
    val title: String? = null,
    val description: String? = null,
    // 공식 인스타그램 게시 동의(#939). 기본값은 JSON에서 생략되고, 서버는 생략을 "동의 안 함"·"ID 없음"으로 본다.
    val instagramUploadAgreed: Boolean = false,
    val instagramId: String? = null,
)

@Serializable
data class PostCreateResponse(
    val postId: String,
    val moderationStatus: String,
)
