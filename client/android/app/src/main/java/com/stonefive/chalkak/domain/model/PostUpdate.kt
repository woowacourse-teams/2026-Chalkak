package com.stonefive.chalkak.domain.model

data class PostUpdate(
    val postId: String,
    val title: String?,
    val description: String?,
    // 저장된 인스타그램 동의. 응답에 없으면 null이고, 그때는 보낸 값이 저장된 것으로 본다.
    val instagramConsent: InstagramConsent? = null,
)
