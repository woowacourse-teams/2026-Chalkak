package com.stonefive.chalkak.domain.repository

/**
 * 피드 상세의 "사인을 눌러 작가 사진 모아보기" 첫 안내 바텀시트를 끝냈는지 기기에 기억한다.
 * 시트를 닫거나 사인을 눌러 작가 사진 화면으로 이동하면 끝난다.
 */
interface SignatureHintRepository {
    /** 안내를 끝낸 적이 있으면 true */
    suspend fun isCompleted(): Boolean

    /** 안내 시트를 닫거나 사인으로 작가 사진 화면에 이동했을 때 호출한다. 이후 [isCompleted]는 true다. */
    suspend fun complete()
}
