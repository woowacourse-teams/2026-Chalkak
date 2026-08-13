package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.home.HomeRemoteDataSource
import com.stonefive.chalkak.data.remote.home.model.HomePhotoResponse
import com.stonefive.chalkak.data.remote.home.model.HomeResponse
import com.stonefive.chalkak.domain.model.Photo
import com.stonefive.chalkak.domain.model.PhotoContent
import com.stonefive.chalkak.domain.model.PhotoSort
import com.stonefive.chalkak.domain.repository.HomeRepository

class HomeRepositoryImpl(private val remoteDataSource: HomeRemoteDataSource) : HomeRepository {
    override suspend fun getHome(sort: PhotoSort): PhotoContent = remoteDataSource.getHome(sort).toDomain()

    override suspend fun updateLike(
        photoId: String,
        isLiked: Boolean,
    ): Int = remoteDataSource
        .updateLike(
            photoId = photoId,
            isLiked = isLiked,
        ).likeCount

    private fun HomeResponse.toDomain(): PhotoContent = PhotoContent(
        dateLabel = dateLabel,
        topic = topic,
        photos = photos.map { it.toDomain() },
        likedPhotoIds = likedPhotoIds,
    )

    private fun HomePhotoResponse.toDomain(): Photo = Photo(
        id = id,
        imageUrl = imageUrl,
        signatureUrl = signatureUrl,
        contentDescription = contentDescription,
        title = title,
        likeCount = likeCount,
    )
}
