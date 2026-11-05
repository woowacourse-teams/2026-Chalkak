package com.stonefive.chalkak.data.remote.post

/**
 * 서버가 인스타그램 ID 형식을 거절할 때의 400 응답 message(이슈 #939).
 * errorCode는 다른 업무 오류와 같은 BUSINESS_ERROR라서 이 문구로만 구분한다.
 * 문구가 바뀌면 일반 실패 안내로 떨어지므로, 전용 errorCode가 생기면 그것으로 바꾼다.
 */
internal const val INVALID_INSTAGRAM_ID_MESSAGE = "인스타그램 ID 형식이 올바르지 않습니다."
