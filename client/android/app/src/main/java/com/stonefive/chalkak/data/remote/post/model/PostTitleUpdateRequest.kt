package com.stonefive.chalkak.data.remote.post.model

import kotlinx.serialization.Serializable

@Serializable
data class PostTitleUpdateRequest(
    val title: String?,
)
