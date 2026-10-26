package com.stonefive.chalkak.domain.model

data class AuthorPost(
    val id: String,
    val originalImageUrl: String,
    val thumbnailImageUrl: String?,
    val topic: Topic,
) {
    val imageUrl: String get() = thumbnailImageUrl ?: originalImageUrl
}

data class AuthorPostsPage(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val posts: List<AuthorPost>,
)
