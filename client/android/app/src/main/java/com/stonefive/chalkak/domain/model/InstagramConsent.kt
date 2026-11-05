package com.stonefive.chalkak.domain.model

/**
 * 게시물을 공식 인스타그램에 소개해도 되는지에 대한 작성자의 동의(이슈 #939).
 *
 * - [agreed]: 운영진이 골라 공식 계정에 올려도 되는지. 작성·수정 요청의 `instagramUploadAgreed`.
 * - [instagramId]: 올릴 때 태그할 작성자 계정. 앱은 입력한 그대로 보내고 형식 검사·정규화는 서버가 한다.
 *   동의하지 않았거나 비워 두면 null이다.
 *
 * 예: 동의하고 "chalkak.daily"를 쓰면 `InstagramConsent(agreed = true, instagramId = "chalkak.daily")`.
 */
data class InstagramConsent(
    val agreed: Boolean,
    val instagramId: String?,
) {
    companion object {
        /** 스위치를 켜지 않은 기본값. */
        val NotAgreed = InstagramConsent(agreed = false, instagramId = null)
    }
}
