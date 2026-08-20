package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.record.RecordRemoteDataSource
import com.stonefive.chalkak.data.remote.record.model.RecordPhotoResponse
import com.stonefive.chalkak.data.remote.record.model.RecordResponse
import com.stonefive.chalkak.domain.model.RecordContent
import com.stonefive.chalkak.domain.model.RecordPhoto
import com.stonefive.chalkak.domain.repository.RecordRepository
import java.time.LocalDate
import java.time.YearMonth

class RecordRepositoryImpl(private val remoteDataSource: RecordRemoteDataSource) : RecordRepository {
    override suspend fun getRecord(month: YearMonth): RecordContent = remoteDataSource.getRecord(month).toDomain()

    private fun RecordResponse.toDomain(): RecordContent = RecordContent(
        month = YearMonth.parse(month),
        photos = photos.map { it.toDomain() },
    )

    private fun RecordPhotoResponse.toDomain(): RecordPhoto = RecordPhoto(
        date = LocalDate.parse(date),
        imageUrl = imageUrl,
        signatureUrl = signatureUrl,
        contentDescription = contentDescription,
        title = title,
    )
}
