package com.stonefive.chalkak.data.remote.record

import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MockRecordRemoteDataSourceTest {
    private val dataSource = MockRecordRemoteDataSource()

    @Test
    fun returnsRemotePhotosWithEmptyDatesForInitialMonth() = runTest {
        val response = dataSource.getRecord(YearMonth.of(2026, 8))

        assertEquals("2026-08", response.month)
        assertEquals(listOf("2026-08"), response.availableMonths)
        assertEquals(24, response.photos.size)
        assertEquals(
            "2026-08-01",
            response.photos
                .first()
                .date,
        )
        assertEquals(
            "https://picsum.photos/seed/chalkak-2026-08-01/600/800.jpg",
            response.photos
                .first()
                .imageUrl,
        )
        assertEquals(
            "https://picsum.photos/seed/chalkak-2026-08-21/800/600.jpg",
            response.photos
                .single { it.date == "2026-08-21" }
                .imageUrl,
        )
        assertTrue(response.photos.none { it.date == "2026-08-04" })
    }

    @Test
    fun returnsEmptyForMonthWithoutPhotos() = runTest {
        val response = dataSource.getRecord(YearMonth.of(2026, 9))

        assertEquals("2026-09", response.month)
        assertEquals(listOf("2026-08"), response.availableMonths)
        assertTrue(response.photos.isEmpty())
    }
}
