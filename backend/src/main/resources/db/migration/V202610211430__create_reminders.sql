-- 관리자가 등록한 리마인더 원본이다. 회원별 알림함 행은 만들지 않고 푸시만 보낸다.
CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    title VARCHAR(50) NOT NULL,
    body VARCHAR(500) NOT NULL,
    repeat VARCHAR(20) NOT NULL,
    target VARCHAR(30) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reminders_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_reminders_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_reminders_repeat CHECK (repeat IN ('ONCE', 'DAILY')),
    CONSTRAINT ck_reminders_target CHECK (target IN ('ALL', 'TOPIC_PARTICIPANTS', 'TOPIC_NON_PARTICIPANTS'))
);
