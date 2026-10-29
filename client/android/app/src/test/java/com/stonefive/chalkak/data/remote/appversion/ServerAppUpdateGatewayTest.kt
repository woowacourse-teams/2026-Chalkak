package com.stonefive.chalkak.data.remote.appversion

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.stonefive.chalkak.core.appupdate.AppUpdateCheckResult
import com.stonefive.chalkak.core.appupdate.AppUpdateGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAppUpdateGatewayTest {
    @Test
    fun `버전은 숫자로 모든 세 자리를 비교한다`() {
        assertTrue(compareVersions("1.9.9", "1.10.0") < 0)
        assertTrue(compareVersions("1.1.0", "1.1.1") < 0)
        assertEquals(0, compareVersions("1.1.1", "1.1.1"))
        assertTrue(compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `미설정과 최소 이상은 Play에 새 버전이 있어도 허용한다`() = runTest {
        val play = FakePlayGateway()
        val api = FakeVersionApi(AppVersionResponse("ANDROID", null, null))
        val gateway = ServerAppUpdateGateway(api, "1.1.1", play)
        assertEquals(AppUpdateCheckResult.NoUpdate, gateway.checkForUpdate())
        api.response = AppVersionResponse("ANDROID", "1.1.1", "https://play.google.com/store/apps/details?id=test")
        assertEquals(AppUpdateCheckResult.NoUpdate, gateway.checkForUpdate())
        api.response = api.response.copy(minimumSupportedVersion = "1.0.9")
        assertEquals(AppUpdateCheckResult.NoUpdate, gateway.checkForUpdate())
        assertEquals(0, play.checks)
    }

    @Test
    fun `Play 조회 실패여도 최소 미달은 스토어 주소와 함께 강제한다`() = runTest {
        val play = FakePlayGateway(AppUpdateCheckResult.Failed)
        val api = FakeVersionApi(AppVersionResponse("ANDROID", "1.1.2", "https://play.google.com/test"))
        val gateway = ServerAppUpdateGateway(api, "1.1.1", play)
        assertEquals(AppUpdateCheckResult.ImmediateUpdateRequired, gateway.checkForUpdate())
        assertEquals("https://play.google.com/test", gateway.storeUrl)
        assertEquals(1, play.checks)
    }

    @Test
    fun `잘못된 플랫폼 버전과 조회 실패는 정책 확인 실패다`() = runTest {
        val api = FakeVersionApi(AppVersionResponse("IOS", "1.1.2", null))
        val gateway = ServerAppUpdateGateway(api, "1.1.1", FakePlayGateway())
        assertEquals(AppUpdateCheckResult.Failed, gateway.checkForUpdate())
        api.response = AppVersionResponse("ANDROID", "1.1", null)
        assertEquals(AppUpdateCheckResult.Failed, gateway.checkForUpdate())
        api.failure = true
        assertEquals(AppUpdateCheckResult.Failed, gateway.checkForUpdate())
    }

    @Test
    fun `매번 서버를 조회하고 이전 강제 업데이트 주소는 실패에도 유지한다`() = runTest {
        val api = FakeVersionApi(AppVersionResponse("ANDROID", "1.1.2", "https://play.google.com/test"))
        val gateway = ServerAppUpdateGateway(api, "1.1.1", FakePlayGateway())
        gateway.checkForUpdate()
        api.failure = true
        assertEquals(AppUpdateCheckResult.Failed, gateway.checkForUpdate())
        assertEquals("https://play.google.com/test", gateway.storeUrl)
        assertEquals(2, api.checks)
    }
}

private class FakeVersionApi(var response: AppVersionResponse) : AppVersionApi {
    var failure = false
    var checks = 0

    override suspend fun getAppVersion(platform: String): AppVersionResponse {
        checks++
        check(platform == "ANDROID")
        check(!failure)
        return response
    }
}

private class FakePlayGateway(val result: AppUpdateCheckResult = AppUpdateCheckResult.ImmediateUpdateRequired) :
    AppUpdateGateway {
    var checks = 0

    override suspend fun checkForUpdate(): AppUpdateCheckResult {
        checks++
        return result
    }

    override fun startImmediateUpdate(activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean =
        false
}
