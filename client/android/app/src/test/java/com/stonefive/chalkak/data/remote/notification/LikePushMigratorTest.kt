package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationFailure
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.LikePushMigrationRepository
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LikePushMigratorTest {
    private val migrationRepository = FakeMigrationRepository()

    @Test
    fun `기존 알림이 모두 꺼져 있으면 좋아요 알림을 끈다`() = runTest {
        val repository = FakeRepository(NotificationPushSettings(false, false, false, true))

        LikePushMigrator(repository, migrationRepository).run(USER_ID)

        assertEquals(listOf(NotificationPushSettingsUpdate(likePushEnabled = false)), repository.updates)
        assertTrue(migrationRepository.isCompleted(USER_ID))
    }

    @Test
    fun `기존 알림이 하나라도 켜져 있으면 좋아요 알림을 켠다`() = runTest {
        listOf(
            NotificationPushSettings(true, false, false, false),
            NotificationPushSettings(false, true, false, false),
            NotificationPushSettings(false, false, true, false),
        ).forEach { settings ->
            val repository = FakeRepository(settings)

            LikePushMigrator(repository, FakeMigrationRepository()).run(USER_ID)

            assertEquals(listOf(NotificationPushSettingsUpdate(likePushEnabled = true)), repository.updates)
        }
    }

    @Test
    fun `이미 맞는 값이면 저장하지 않고 완료한다`() = runTest {
        val repository = FakeRepository(NotificationPushSettings(true, false, false, true))

        LikePushMigrator(repository, migrationRepository).run(USER_ID)

        assertTrue(repository.updates.isEmpty())
        assertTrue(migrationRepository.isCompleted(USER_ID))
    }

    @Test
    fun `같은 회원은 한 번만 실행하고 다른 회원은 따로 실행한다`() = runTest {
        val repository = FakeRepository(NotificationPushSettings(false, false, false, true))
        val migrator = LikePushMigrator(repository, migrationRepository)

        migrator.run(USER_ID)
        repository.settings = NotificationPushSettings(false, false, false, true)
        migrator.run(USER_ID)
        assertEquals(1, repository.loadCount)

        migrator.run("other-user")
        assertEquals(2, repository.loadCount)
    }

    @Test
    fun `조회가 실패하면 완료로 기록하지 않는다`() = runTest {
        val repository = FakeRepository(NotificationPushSettings(false, false, false, true)).apply {
            loadResult = NotificationResult.Failure(NotificationFailure.Network)
        }

        LikePushMigrator(repository, migrationRepository).run(USER_ID)

        assertTrue(repository.updates.isEmpty())
        assertFalse(migrationRepository.isCompleted(USER_ID))
    }

    @Test
    fun `저장이 실패하면 완료로 기록하지 않아 다시 시도한다`() = runTest {
        val repository = FakeRepository(NotificationPushSettings(false, false, false, true)).apply {
            updateResult = NotificationResult.Failure(NotificationFailure.Network)
        }
        val migrator = LikePushMigrator(repository, migrationRepository)

        migrator.run(USER_ID)
        assertFalse(migrationRepository.isCompleted(USER_ID))

        repository.updateResult = NotificationResult.Success(Unit)
        migrator.run(USER_ID)

        assertEquals(2, repository.updates.size)
        assertTrue(migrationRepository.isCompleted(USER_ID))
    }
}

private const val USER_ID = "user-id"

private class FakeMigrationRepository : LikePushMigrationRepository {
    private val completed = mutableSetOf<String>()

    override suspend fun isCompleted(userId: String): Boolean = userId in completed

    override suspend fun complete(userId: String) {
        completed += userId
    }
}

private class FakeRepository(var settings: NotificationPushSettings) : NotificationRepository {
    var loadResult: NotificationResult<NotificationPushSettings>? = null
    var updateResult: NotificationResult<Unit> = NotificationResult.Success(Unit)
    var loadCount = 0
    val updates = mutableListOf<NotificationPushSettingsUpdate>()

    override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> {
        loadCount += 1
        return loadResult ?: NotificationResult.Success(settings)
    }

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> {
        updates += settings
        return updateResult
    }

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage> = error("Not used")

    override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
        error("Not used")

    override suspend fun getUnreadStatus(): NotificationResult<Boolean> = error("Not used")

    override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = error("Not used")

    override suspend fun markAllAsRead(): NotificationResult<Unit> = error("Not used")

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> = error("Not used")
}
