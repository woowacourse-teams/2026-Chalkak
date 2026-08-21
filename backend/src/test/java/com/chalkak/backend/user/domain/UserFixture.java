package com.chalkak.backend.user.domain;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 운영 코드에 테스트 전용 생성자나 setter를 뚫지 않기 위해 리플렉션으로 필드를 채운다. 회원가입 유스케이스가 생기면 정식 생성 경로로 교체한다.
 */
public final class UserFixture {

    private UserFixture() {
    }

    public static User create() {
        return create(null);
    }

    /**
     * 비식별화 더미값이 {@code user_id}에 의존하므로 저장 없이 검증하는 도메인 테스트는 {@code id}를 직접 넣어야 한다.
     */
    public static User create(UUID id) {
        String unique = (id == null) ? UUID.randomUUID().toString() : id.toString();

        User user = new User();
        setField(user, "id", id);
        setField(user, "email", "user-" + unique + "@chalkak.test");
        setField(user, "status", UserStatus.ACTIVE);
        setField(user, "signatureOriginalStorageKey", "signatures/" + unique);
        setField(user, "signatureThumbnailStorageKey", "thumbnails/" + unique);
        return user;
    }

    private static void setField(User user, String name, Object value) {
        try {
            Field field = User.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(user, value);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new IllegalStateException("User의 " + name + " 필드를 설정할 수 없습니다.", exception);
        }
    }
}
