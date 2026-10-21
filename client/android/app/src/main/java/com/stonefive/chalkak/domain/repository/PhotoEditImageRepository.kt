package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.photoedit.PhotoPixels

interface PhotoEditImageRepository {
    /** EXIF 방향을 반영하고 긴 변을 [minLongEdge] 이상으로 남기며 줄인 픽셀. 읽지 못하면 `null`이다. */
    suspend fun loadPixels(
        imageUri: String,
        minLongEdge: Int,
    ): PhotoPixels?
}
