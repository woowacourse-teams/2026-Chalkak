package com.stonefive.chalkak.domain.model

import java.time.LocalDate

data class HomeQuery(
    val date: LocalDate,
    val sort: PostSort,
    val page: Int,
    val pageSize: Int = PAGE_SIZE,
    val randomSeed: String? = null,
) {
    init {
        require(page >= FIRST_PAGE)
        require(pageSize == PAGE_SIZE)
        require(sort == PostSort.RANDOM || randomSeed == null)
        require(sort != PostSort.RANDOM || page == FIRST_PAGE || randomSeed != null)
    }

    companion object {
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 20
    }
}

data class PostPage(
    val photos: List<Post>,
    val likedPhotoIds: Set<String>,
    val currentPage: Int,
    val hasNext: Boolean,
    val randomSeed: String?,
)

data class HomeLike(
    val likeCount: Int,
    val isLiked: Boolean,
)

sealed interface HomeResult<out T> {
    data class Success<T>(val value: T) : HomeResult<T>

    data class Failure(val reason: HomeFailure) : HomeResult<Nothing>
}

sealed interface HomeFailure {
    data object TopicNotFound : HomeFailure

    data object Unauthorized : HomeFailure

    data object Network : HomeFailure

    data object InvalidResponse : HomeFailure

    data class Http(val statusCode: Int) : HomeFailure
}
