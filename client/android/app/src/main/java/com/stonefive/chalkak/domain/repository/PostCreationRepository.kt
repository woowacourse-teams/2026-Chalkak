package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopic
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import java.time.LocalDate

interface PostCreationRepository {
    suspend fun getCreationTopic(topicDate: LocalDate): PostCreationTopicResult

    suspend fun createPost(
        imageUri: String,
        title: String?,
        topic: PostCreationTopic,
    ): PostCreationResult
}
