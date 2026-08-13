package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.home.HomeRemoteDataSource
import com.stonefive.chalkak.data.remote.home.model.HomePhotoResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.HomeContent
import com.stonefive.chalkak.domain.model.HomePhoto
import com.stonefive.chalkak.domain.model.HomeSort
import com.stonefive.chalkak.domain.repository.HomeRepository

class HomeRepositoryImpl(private val remoteDataSource: HomeRemoteDataSource) : HomeRepository {
    override suspend fun getHome(sort: HomeSort): HomeContent = remoteDataSource.getHome(sort).toDomain()

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int = remoteDataSource
        .updateLike(
            photoId = photoId,
            isLiked = isLiked,
        ).likeCount

    private fun HomeResponse.toDomain(): HomeContent = HomeContent(
        dateLabel = dateLabel,
        topic = topic,
        photos = photos.map { it.toDomain() },
        likedPhotoIds = likedPhotoIds,
    )

    private fun HomePhotoResponse.toDomain(): HomePhoto = HomePhoto(
        id = id,
        imageUrl = imageUrl,
        signatureUrl = signatureUrl,
        contentDescription = contentDescription,
        story = story,
        likeCount = likeCount,
    )
}
