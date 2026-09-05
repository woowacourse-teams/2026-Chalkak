package com.chalkak.backend.auth.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 인증 정보가 가리키는 회원이 남아 있어야만 호출할 수 있는 엔드포인트에 붙인다.
 *
 * <p>{@link RequiresUsableUser}와 달리 정지는 막지 않는다. 정지 회원도 탈퇴와 자기 데이터
 * 정리는 할 수 있어야 하므로, 조회와 정리 경로에는 정지가 아니라 부재만 걸러야 한다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@usableUserPolicy.validateExisting(authentication)")
public @interface RequiresExistingUser {
}
