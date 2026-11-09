package com.stonefive.chalkak.domain.model

/** 내 문의 목록·상세·삭제가 실패한 이유. 접수(제출) 실패는 [FeedbackSubmissionFailure]를 쓴다. */
enum class FeedbackInquiryFailure {
    /** 401. 재인증이 필요하다. */
    UNAUTHORIZED,

    /** 404. 본인 문의가 아니거나 삭제됐거나 없는 문의다. 원인을 구분하지 않는다. */
    NOT_FOUND,
    NETWORK,
    UNKNOWN,
}

class FeedbackInquiryException(val reason: FeedbackInquiryFailure) : Exception()
