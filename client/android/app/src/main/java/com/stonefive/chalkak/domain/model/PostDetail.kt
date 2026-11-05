package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class PostDetail(
    val post: Post,
    val topic: String,
    val topicDate: LocalDate,
    // 본인 게시물일 때만 서버가 내려 준다. 다른 사람 게시물이거나 받지 못하면 null.
    val instagramConsent: InstagramConsent? = null,
)
