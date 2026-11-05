package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostUpdateResponse(
    val postId: String,
    val title: String? = null,
    val description: String? = null,
)
