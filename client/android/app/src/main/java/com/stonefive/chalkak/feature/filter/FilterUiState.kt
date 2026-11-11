package com.stonefive.chalkak.feature.filter

import androidx.annotation.DrawableRes

/** 필터 API가 붙기 전까지 화면에서 쓰는 필터 한 건. 이미지는 앱 리소스다. */
data class FilterItem(
    val id: String,
    val name: String,
    val description: String,
    val creatorSignature: String,
    @param:DrawableRes val coverImageRes: Int,
    val creatorPhotoResList: List<Int>,
    val usedPhotoResList: List<Int>,
)

data class FilterUiState(
    val filters: List<FilterItem> = emptyList(),
    val selectedFilterId: String? = null,
    val favoriteFilterIds: Set<String> = emptySet(),
) {
    val selectedFilter: FilterItem?
        get() = filters.firstOrNull { it.id == selectedFilterId }

    fun isFavorite(filterId: String): Boolean = filterId in favoriteFilterIds
}
