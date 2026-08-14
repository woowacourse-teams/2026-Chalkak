package com.stonefive.chalkak.feature.home

internal fun HomeUiState.reduce(action: HomeUiAction): HomeUiState = when (action) {
    is HomeUiAction.SortSelected -> copy(selectedSort = action.sort)

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

    is HomeUiAction.BottomBarSelected -> copy(selectedBottomBarItem = action.item)

    HomeUiAction.AddClicked -> this
}
