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
    fun disablesMonthsWithoutPhotosBeforeNavigation() = runTest {
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.canGoPrevious)
        assertEquals(false, viewModel.uiState.value.canGoNext)

        viewModel.moveToPreviousMonth()
        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(INITIAL_RECORD_MONTH, viewModel.uiState.value.month)
        assertEquals(listOf(INITIAL_RECORD_MONTH), repository.requests)
    }

    @Test
    fun movesOnlyToAvailableAdjacentMonth() = runTest {
        val nextMonth = INITIAL_RECORD_MONTH.plusMonths(1)
        repository = FakeRecordRepository(
            availableMonths = setOf(INITIAL_RECORD_MONTH, nextMonth),
        )
        viewModel = RecordViewModel(repository)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.canGoNext)

        viewModel.moveToNextMonth()
        advanceUntilIdle()

        assertEquals(nextMonth, viewModel.uiState.value.month)
        assertEquals(false, viewModel.uiState.value.canGoNext)
        assertEquals(true, viewModel.uiState.value.canGoPrevious)
        assertEquals(listOf(INITIAL_RECORD_MONTH, nextMonth), repository.requests)
    }
}

private class FakeRecordRepository(private val availableMonths: Set<YearMonth> = setOf(INITIAL_RECORD_MONTH)) :
    RecordRepository {
    val requests = mutableListOf<YearMonth>()

    override suspend fun getRecord(month: YearMonth): RecordContent {
        requests += month
        return RecordContent(
            month = month,
            photos = if (month in availableMonths) {
                listOf(
                    recordPhoto(month = month, day = 2),
                    recordPhoto(month = month, day = 5),
                )
            } else {
                emptyList()
            },
            availableMonths = availableMonths,
        )
    }

    private fun recordPhoto(
        month: YearMonth,
        day: Int,
    ): RecordPhoto = RecordPhoto(
        date = month.atDay(day),
        imageUrl = "photo-$day",
        signatureUrl = "signature-$day",
        contentDescription = "사진 $day",
        title = "물결",
    )
}
