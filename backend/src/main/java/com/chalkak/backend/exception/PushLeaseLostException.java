package com.chalkak.backend.exception;

// 기기 하나의 실패와 달리, 현재 작업에서 더 이상의 발송을 시작하지 않고 SQS 재처리로 넘긴다.
public class PushLeaseLostException extends BusinessException {
    public PushLeaseLostException() {
        super(ErrorCode.BUSINESS_ERROR, "푸시 발송 처리 권한을 유지할 수 없습니다.");
    }
}
