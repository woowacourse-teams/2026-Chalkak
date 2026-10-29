package com.stonefive.chalkak.domain.repository

/** 기록 탭에서 고른 보기 방식. 앱을 다시 열어도 유지한다. */
interface RecordPreferenceRepository {
    /** 월간 사진 묶음 모양의 이름. 고른 적이 없으면 null이다. */
    suspend fun getPhotoStackStyle(): String?

    suspend fun setPhotoStackStyle(name: String)
}
