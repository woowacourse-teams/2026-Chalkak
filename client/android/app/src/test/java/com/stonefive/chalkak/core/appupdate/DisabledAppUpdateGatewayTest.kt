package com.stonefive.chalkak.core.appupdate

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DisabledAppUpdateGatewayTest {
    @Test
    fun `업데이트 확인 시 업데이트가 필요하지 않음을 반환한다`() = runTest {
        val gateway = DisabledAppUpdateGateway()

        val actual = gateway.checkForUpdate()

        assertEquals(AppUpdateCheckResult.NoUpdate, actual)
    }
}
