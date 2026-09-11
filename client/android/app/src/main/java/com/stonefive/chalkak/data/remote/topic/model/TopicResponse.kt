package com.stonefive.chalkak.data.remote.topic.model

import kotlinx.serialization.Serializable

@Serializable
data class TopicResponse(
    val id: String,
    val title: String,
    val topicDate: String,
)
