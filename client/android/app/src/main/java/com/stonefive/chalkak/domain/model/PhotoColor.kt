package com.stonefive.chalkak.domain.model

/** 사진의 대표색(0..1 RGB). 연간 달력 칸과 책자 표지를 칠한다. */
data class PhotoColor(
    val red: Float,
    val green: Float,
    val blue: Float,
)
