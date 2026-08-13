package com.stonefive.chalkak.domain.model

data class HomePhoto(
    val id: String,
    val image: HomeImage,
    val signatureImage: HomeImage?,
    val contentDescription: String,
    val story: String?,
    val likeCount: Int,
)
