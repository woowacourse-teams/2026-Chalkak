package com.stonefive.chalkak.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val errorCode: String? = null,
    val message: String? = null,
)
