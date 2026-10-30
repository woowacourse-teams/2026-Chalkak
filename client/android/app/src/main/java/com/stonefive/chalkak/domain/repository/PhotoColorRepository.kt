package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PhotoColor

interface PhotoColorRepository {
    /** 사진의 대표색. 구하지 못하면 null이다. 사진 색은 바뀌지 않으므로 게시물 ID별로 저장해 두고 다시 구하지 않는다. */
    suspend fun getPhotoColor(
        postId: String,
        imageUrl: String,
    ): PhotoColor?
}
