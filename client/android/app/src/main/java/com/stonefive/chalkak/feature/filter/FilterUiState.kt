package com.stonefive.chalkak.feature.filter

import androidx.annotation.DrawableRes
import com.stonefive.chalkak.R

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

// 필터 API 연동 전 임시 데이터이며, 연동 시 data 계층 조회로 교체한다.
internal val SampleFilters = List(6) { index ->
    FilterItem(
        id = "sample-$index",
        name = "필터 ${index + 1}",
        description = "언제 언제 쓰면 예뻐요",
        creatorSignature = "제작자\n사인",
        coverImageRes = R.drawable.home_feed_photo,
        creatorPhotoResList = List(4) { R.drawable.home_feed_photo },
        usedPhotoResList = List(3) { R.drawable.home_feed_photo },
    )
}
