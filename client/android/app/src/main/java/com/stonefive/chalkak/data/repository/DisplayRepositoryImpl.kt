package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.display.DisplayRemoteDataSource
import com.stonefive.chalkak.data.remote.display.model.DisplayPhotoResponse
import com.stonefive.chalkak.data.remote.display.model.DisplayResponse
import com.stonefive.chalkak.domain.model.DisplayContent
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.DisplayRepository
import java.time.LocalDate

class DisplayRepositoryImpl(private val remoteDataSource: DisplayRemoteDataSource) : DisplayRepository {
    override suspend fun getDisplay(
        date: LocalDate?,
        sort: PostSort,
    ): DisplayContent = remoteDataSource.getDisplay(date, sort).toDomain()

    private fun DisplayResponse.toDomain(): DisplayContent = DisplayContent(
        selectedDate = LocalDate.parse(selectedDate),
        latestDate = LocalDate.parse(latestDate),
        earliestDate = LocalDate.parse(earliestDate),
        topic = topic,
        photos = photos.map { it.toDomain() },
        featuredPhotos = featuredPhotos.map { it.toDomain() },
    )

    private fun DisplayPhotoResponse.toDomain(): Post = Post(
        id = id,
        imageUrl = imageUrl,
        signatureUrl = signatureUrl,
        contentDescription = contentDescription,
        title = title,
        likeCount = likeCount,
    )
}
