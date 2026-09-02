package com.chalkak.backend.exception;

public enum ErrorCode {
    BUSINESS_ERROR,
    SIGNATURE_REGISTRATION_REQUIRED,
    SIGNATURE_REUPLOAD_REQUIRED,
    SIGNATURE_PROCESSING_PENDING,
    RESOURCE_STATE_CHANGED,
    UNAUTHORIZED,
    // UNAUTHORIZED는 액세스 토큰만 만료된 상태라 클라이언트가 재발급 후 요청을 다시 보내면 된다.
    // 이 코드는 재발급까지 실패해 저장한 토큰을 버리고 로그인 화면으로 보내야 하는 경우를 가른다.
    REAUTHENTICATION_REQUIRED,
    FORBIDDEN,
    INTERNAL_ERROR
}
