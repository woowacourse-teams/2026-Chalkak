package com.stonefive.chalkak.domain.model

data class Photo(
    val id: String,
    val imageUrl: String,
    val signatureUrl: String?,
    val contentDescription: String,
    val story: String?,
    val likeCount: Int,
)
