package com.stonefive.chalkak.domain.model

data class Banner(
    val id: String,
    val title: String,
    val subtitle: String,
    val webViewUrl: String,
    val thumbnailUrl: String,
    val createdAt: String,
)

data class BannerPage(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val banners: List<Banner>,
)
