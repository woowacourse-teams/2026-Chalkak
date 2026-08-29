package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PostCreationResult
import java.time.LocalDate

interface PostRepository {
    suspend fun createPost(
        imageUri: String,
        title: String?,
        topicDate: LocalDate,
    ): PostCreationResult
}
