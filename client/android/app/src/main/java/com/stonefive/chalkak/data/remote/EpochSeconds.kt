package com.stonefive.chalkak.data.remote

internal fun Long.plusExpiresInSaturating(expiresInSeconds: Long): Long =
    if (expiresInSeconds > Long.MAX_VALUE - this) Long.MAX_VALUE else this + expiresInSeconds
