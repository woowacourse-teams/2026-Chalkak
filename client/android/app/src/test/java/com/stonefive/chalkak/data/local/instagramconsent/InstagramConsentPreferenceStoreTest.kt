package com.stonefive.chalkak.data.local.instagramconsent

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.stonefive.chalkak.domain.model.InstagramConsent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InstagramConsentPreferenceStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `계정별 저장과 OFF 정규화 및 재시작 복원을 지원한다`() = runTest {
        val file = temporaryFolder.newFolder().resolve("consent.preferences_pb")
        val firstJob = SupervisorJob()
        val first = InstagramConsentPreferenceStore(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO)) { file },
        )
        assertEquals(null, first.get("a"))
        first.save("a", InstagramConsent(true, "chalkak"))
        first.save("b", InstagramConsent(false, "hidden"))
        assertEquals(InstagramConsent.NotAgreed, first.get("b"))
        firstJob.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val reopened = InstagramConsentPreferenceStore(
                PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO)) { file },
            )
            assertEquals(InstagramConsent(true, "chalkak"), reopened.get("a"))
            assertEquals(InstagramConsent.NotAgreed, reopened.get("b"))
            reopened.clear("a")
            reopened.save("a", InstagramConsent(true, "late-success"))
            assertEquals(null, reopened.get("a"))
            assertEquals(InstagramConsent.NotAgreed, reopened.get("b"))
        } finally {
            secondJob.cancelAndJoin()
        }
    }
}
