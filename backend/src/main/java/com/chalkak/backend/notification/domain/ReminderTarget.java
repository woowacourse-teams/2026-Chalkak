package com.chalkak.backend.notification.domain;

public enum ReminderTarget {
    ALL, // 전체 회원
    TOPIC_PARTICIPANTS, // 오늘 주제 참여자
    TOPIC_NON_PARTICIPANTS // 오늘 주제 미참여자
}
