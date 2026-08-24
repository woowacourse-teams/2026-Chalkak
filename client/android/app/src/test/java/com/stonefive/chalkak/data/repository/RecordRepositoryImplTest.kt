package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.record.RecordRemoteDataSource
import com.stonefive.chalkak.data.remote.record.model.RecordPhotoResponse
import com.stonefive.chalkak.data.remote.record.model.RecordResponse
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordRepositoryImplTest {
    @Test
    fun mapsResponseToRecordContent() = runTest {
        val repository = RecordRepositoryImpl(
            remoteDataSource = FakeRecordRemoteDataSource(),
        )

        val content = repository.getRecord(YearMonth.of(2026, 8))

        assertEquals(YearMonth.of(2026, 8), content.month)
        assertEquals(
            setOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8)),
            content.availableMonths,
        )
        assertEquals(1, content.photos.size)
        assertEquals(
            2,
            content.photos
                .single()
                .date.dayOfMonth,
        )
        assertEquals(
            "물결",
            content.photos
                .single()
                .title,
        )
    }
}

private class FakeRecordRemoteDataSource : RecordRemoteDataSource {
    override suspend fun getRecord(month: YearMonth): RecordResponse = RecordResponse(
        month = month.toString(),
        availableMonths = listOf("2026-07", "2026-08"),
        photos = listOf(
            RecordPhotoResponse(
                date = "2026-08-02",
                imageUrl = "photo",
                signatureUrl = "signature",
                contentDescription = "노을과 전신주",
                title = "물결",
            ),
        ),
    )
}
