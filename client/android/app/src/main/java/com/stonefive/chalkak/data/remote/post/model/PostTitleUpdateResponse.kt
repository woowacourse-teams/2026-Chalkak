package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostTitleUpdateResponse(
    val postId: String,
    val title: String? = null,
)
