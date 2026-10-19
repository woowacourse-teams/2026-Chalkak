package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostImagePreparation
import com.stonefive.chalkak.domain.model.PostImagePreparationResult
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.model.photoedit.PhotoEdit
import java.time.LocalDate

interface PostCreationRepository {
    fun getCachedCreationTopic(topicDate: LocalDate): Topic?

    suspend fun getCreationTopic(topicDate: LocalDate): PostCreationTopicResult

    suspend fun prepareImage(
        imageUri: String,
        edit: PhotoEdit,
    ): PostImagePreparationResult

    fun discardPreparedImage(preparation: PostImagePreparation)

    suspend fun createPost(
        preparation: PostImagePreparation,
        title: String?,
        topic: Topic,
    ): PostCreationResult
}
