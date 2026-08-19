package com.stonefive.chalkak.domain.model

data class Post(
    val id: String,
    val imageUrl: String,
    val signatureUrl: String,
    val contentDescription: String,
    val title: String?,
    val likeCount: Int,
)
