package com.chalkak.backend.aireview.service;

/**
 * 처리를 끝내지 못한 채 행을 처리 중으로 남기는 사유. 로그의 {@code reason} 값으로만 쓰며 DB에는 저장하지 않는다.
 */
public enum AbandonReason {

    /** 종료·interrupt로 처리를 멈춘 경우 */
    SHUTDOWN,
    /** 처리 도중 예상 못 한 예외로 멈춘 경우 */
    UNEXPECTED_WHILE_PROCESSING,
    /** 결과를 반영하는 DB 단계가 실패한 경우 */
    DB_ERROR,
    /** 호출 전 준비 단계(게시물 조회·요청 생성)가 실패한 경우 */
    PREPARE_FAILED
}
