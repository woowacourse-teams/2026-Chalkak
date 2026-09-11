package com.stonefive.chalkak.domain.model

import java.time.LocalDate
import org.junit.Assert.assertThrows
import org.junit.Test

class HomeContractTest {
    @Test
    fun `RANDOM 첫 페이지는 seed를 허용하지 않는다`() {
        assertThrows(IllegalArgumentException::class.java) {
            homeQuery(
                page = HomeQuery.FIRST_PAGE,
                randomSeed = "existing-seed",
            )
        }
    }

    @Test
    fun `RANDOM 다음 페이지는 비어 있지 않은 seed가 필요하다`() {
        listOf(null, "", "   ").forEach { seed ->
            assertThrows(IllegalArgumentException::class.java) {
                homeQuery(
                    page = HomeQuery.FIRST_PAGE + 1,
                    randomSeed = seed,
                )
            }
        }
    }

    private fun homeQuery(
        page: Int,
        randomSeed: String?,
    ) = HomeQuery(
        date = LocalDate.of(2026, 8, 28),
        sort = PostSort.RANDOM,
        page = page,
        randomSeed = randomSeed,
    )
}
