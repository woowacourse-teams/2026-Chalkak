package com.chalkak.backend.user.domain;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 테스트용 {@link User} 생성 도구.
 *
 * <p>{@code User}는 생성자가 {@code protected} 하나뿐이고 필드에 setter가 없다. 운영 코드에 테스트 전용 생성자를 뚫지 않기 위해 리플렉션으로 필드를 채운다.
 * 회원가입 유스케이스가 생기면 정식 생성 경로로 교체한다.
 */
public final class UserFixture {

    private UserFixture() {
    }

    /**
     * 저장 전 사용자를 만든다. {@code id}는 저장 시 DB가 생성한다.
     */
    public static User create() {
        return create(null);
    }

    /**
     * {@code id}가 주입된 사용자를 만든다. 비식별화 더미값이 {@code user_id}에 의존하므로 저장 없이 검증하는 도메인 테스트에 사용한다.
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
