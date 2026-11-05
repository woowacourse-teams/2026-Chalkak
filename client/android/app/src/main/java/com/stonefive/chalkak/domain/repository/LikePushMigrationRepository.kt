package com.stonefive.chalkak.domain.repository

interface LikePushMigrationRepository {
    suspend fun isCompleted(userId: String): Boolean

    suspend fun complete(userId: String)
}
