package com.chalkak.backend.exception;

// 기록이 불명확해지면 현재 사용자 작업을 중단하고 SQS 재처리로 넘긴다.
public class PushDeliveryRecordException extends RuntimeException {
    public PushDeliveryRecordException() {
        super("푸시 발송 이력을 저장하지 못했습니다.");
    }
}
