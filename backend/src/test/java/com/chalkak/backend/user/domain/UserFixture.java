package com.chalkak.backend.user.domain;

import java.lang.reflect.Field;
import java.util.UUID;

/** 운영 코드에 테스트 전용 생성자나 setter를 뚫지 않기 위한 픽스처다. */
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

        User user = User.create(
                "user-" + unique + "@chalkak.test",
                new SignatureStorageKeys(
                        "signatures/" + unique,
                        "thumbnails/" + unique));
        setField(user, "id", id);
        return user;
    }

    public static User createBanned(UUID id) {
        User user = create(id);
        setField(user, "status", UserStatus.BANNED);
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
