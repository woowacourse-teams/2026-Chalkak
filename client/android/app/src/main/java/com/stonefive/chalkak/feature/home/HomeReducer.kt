package com.stonefive.chalkak.feature.home

internal fun HomeUiState.reduce(action: HomeUiAction): HomeUiState = when (action) {
    is HomeUiAction.SortSelected -> copy(
        selectedSort = action.sort,
        expandedStoryPhotoIds = emptySet(),
    )

    is HomeUiAction.LikeClicked -> {
        val liked = action.photoId !in likedPhotoIds
        copy(
            photos = photos.map { photo ->
                if (photo.id == action.photoId) {
                    photo.copy(likeCount = photo.likeCount + if (liked) 1 else -1)
                } else {
                    photo
                }
            },
            likedPhotoIds = if (liked) {
                likedPhotoIds + action.photoId
            } else {
                likedPhotoIds - action.photoId
            },
        )
    }

    is HomeUiAction.StoryClicked -> if (photos.none { it.id == action.photoId && it.story != null }) {
        copy(expandedStoryPhotoIds = expandedStoryPhotoIds - action.photoId)
    } else {
        copy(
            expandedStoryPhotoIds = if (action.photoId in expandedStoryPhotoIds) {
                expandedStoryPhotoIds - action.photoId
            } else {
                expandedStoryPhotoIds + action.photoId
            },
        )
    }

    is HomeUiAction.BottomBarSelected -> copy(selectedBottomBarItem = action.item)

    HomeUiAction.AddClicked -> this
}
