package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostUpdateResponse(
    val postId: String,
    val title: String? = null,
    val description: String? = null,
    // 저장된 인스타그램 동의. 서버가 아직 내려 주지 않으면 null이다.
    val instagramUploadAgreed: Boolean? = null,
    val instagramId: String? = null,
)
