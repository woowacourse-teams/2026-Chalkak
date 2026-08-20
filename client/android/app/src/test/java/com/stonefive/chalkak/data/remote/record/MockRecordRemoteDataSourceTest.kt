package com.stonefive.chalkak.data.remote.record

import com.stonefive.chalkak.R
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRecordRemoteDataSourceTest {
    private val dataSource = MockRecordRemoteDataSource()

    @Test
    fun returns14PhotosForInitialMonth() = runTest {
        val response = dataSource.getRecord(YearMonth.of(2026, 8))

        assertEquals("2026-08", response.month)
        assertEquals(14, response.photos.size)
        assertEquals(
            "2026-08-02",
            response.photos
                .first()
                .date,
        )
        assertEquals(
            "android.resource://com.stonefive.chalkak/${R.drawable.record_landscape_photo}",
            response.photos
                .single { it.date == "2026-08-21" }
                .imageUrl,
        )
    }

    @Test
    fun returnsEmptyForMonthWithoutPhotos() = runTest {
        val response = dataSource.getRecord(YearMonth.of(2026, 9))

        assertEquals("2026-09", response.month)
        assertTrue(response.photos.isEmpty())
    }
}
