package com.stonefive.chalkak.feature.record

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.domain.model.RecordContent
import com.stonefive.chalkak.domain.model.RecordPhoto
import com.stonefive.chalkak.domain.repository.RecordRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: FakeRecordRepository
    private lateinit var viewModel: RecordViewModel

    @Before
    fun setUp() {
        repository = FakeRecordRepository()
        viewModel = RecordViewModel(repository)
    }

    @Test
    fun loadsInitialMonth() = runTest {
        advanceUntilIdle()

        assertEquals(INITIAL_RECORD_MONTH, viewModel.uiState.value.month)
        assertEquals(
            2,
            viewModel.uiState.value.selectedDate
                ?.dayOfMonth,
        )
        assertEquals(2, viewModel.uiState.value.photos.size)
        assertEquals(listOf(INITIAL_RECORD_MONTH), repository.requests)
    }

    @Test
    fun selectsPhotoDate() = runTest {
        advanceUntilIdle()

        viewModel.selectDate(LocalDate.of(2026, 8, 5))

        assertEquals(LocalDate.of(2026, 8, 5), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun keepsSelectionForDateWithoutPhoto() = runTest {
        advanceUntilIdle()

        viewModel.selectDate(LocalDate.of(2026, 8, 6))

        assertEquals(LocalDate.of(2026, 8, 2), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun doesNotMoveToMonthWithoutPhotos() = runTest {
        advanceUntilIdle()

        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(INITIAL_RECORD_MONTH, viewModel.uiState.value.month)
        assertEquals(2, viewModel.uiState.value.photos.size)
        assertEquals(LocalDate.of(2026, 8, 2), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.canGoNext)
        assertEquals(
            listOf(INITIAL_RECORD_MONTH, INITIAL_RECORD_MONTH.plusMonths(1)),
            repository.requests,
        )
    }
}

private class FakeRecordRepository : RecordRepository {
    val requests = mutableListOf<YearMonth>()

    override suspend fun getRecord(month: YearMonth): RecordContent {
        requests += month
        return RecordContent(
            month = month,
            photos = if (month == INITIAL_RECORD_MONTH) {
                listOf(
                    recordPhoto(day = 2),
                    recordPhoto(day = 5),
                )
            } else {
                emptyList()
            },
        )
    }

    private fun recordPhoto(day: Int): RecordPhoto = RecordPhoto(
        date = LocalDate.of(2026, 8, day),
        imageUrl = "photo-$day",
        signatureUrl = "signature-$day",
        contentDescription = "사진 $day",
        title = "물결",
    )
}
