package com.stonefive.chalkak.feature.instagramconsent

import androidx.compose.runtime.Immutable
import com.stonefive.chalkak.domain.model.InstagramConsent

/**
 * 전시하기 화면과 게시물 수정 시트가 함께 쓰는 "공식 인스타그램 소개 허용" 입력 상태(이슈 #939).
 *
 * - [agreed]: 스위치가 켜져 있는지.
 * - [instagramId]: ID 칸에 쓴 글. 스위치를 꺼도 지우지 않아, 다시 켜면 쓰던 ID가 남아 있다.
 * - [instagramIdError]: 서버가 ID 형식을 거절했을 때 ID 칸 아래에 보여 줄 문구. 없으면 null.
 *
 * 서버로 보낼 값은 [toConsent]로 만든다. 스위치가 꺼져 있거나 ID가 공백뿐이면 ID는 null로 보낸다.
 */
@Immutable
data class InstagramConsentInputState(
    val agreed: Boolean = false,
    val instagramId: String = "",
    val instagramIdError: String? = null,
) {
    /** 스위치를 켜거나 끈다. 이전 ID 오류는 지운다. */
    fun withAgreed(agreed: Boolean): InstagramConsentInputState = copy(agreed = agreed, instagramIdError = null)

    /** ID 칸 입력. [INSTAGRAM_ID_MAX_LENGTH]자까지만 받고, 고치면 이전 ID 오류는 지운다. */
    fun withInstagramId(instagramId: String): InstagramConsentInputState = copy(
        instagramId = instagramId.take(INSTAGRAM_ID_MAX_LENGTH),
        instagramIdError = null,
    )

    /** 서버가 ID 형식을 거절했을 때. 스위치와 입력한 ID는 그대로 둔다. */
    fun withInvalidInstagramId(): InstagramConsentInputState = copy(instagramIdError = INVALID_INSTAGRAM_ID_MESSAGE)

    /**
     * 요청에 실을 값. ID는 입력한 그대로 보낸다(앞의 `@` 제거·소문자 변환은 서버가 한다).
     * 예: 켜짐 + "chalkak.daily" → (true, "chalkak.daily"), 켜짐 + "  " → (true, null), 꺼짐 + "chalkak.daily" → (false, null)
     */
    fun toConsent(): InstagramConsent = InstagramConsent(
        agreed = agreed,
        instagramId = instagramId.takeIf { agreed && it.isNotBlank() },
    )
}

/** 상세에서 받은 본인 동의를 수정 시트의 시작 입력으로 바꾼다. ID가 없으면 빈 칸이다. */
fun InstagramConsent.toInputState(): InstagramConsentInputState = InstagramConsentInputState(
    agreed = agreed,
    instagramId = instagramId.orEmpty(),
)

/** 인스타그램 ID 최대 글자 수. 인스타그램 사용자 이름 한도와 같다. */
const val INSTAGRAM_ID_MAX_LENGTH = 30

const val INVALID_INSTAGRAM_ID_MESSAGE = "영문, 숫자, 마침표(.), 밑줄(_)만 쓸 수 있어요"
