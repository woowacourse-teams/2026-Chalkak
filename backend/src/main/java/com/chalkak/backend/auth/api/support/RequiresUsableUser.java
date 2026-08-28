package com.chalkak.backend.auth.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * 정지된 회원이 호출할 수 없는 엔드포인트에 붙인다.
 *
 * <p>정지는 남에게 보이는 것을 새로 만들거나 바꾸는 행위만 막는다. 조회와 자기 데이터 정리(탈퇴,
 * 좋아요 취소)는 정지 중에도 할 수 있어야 하므로 이 표시를 붙이지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@usableUserPolicy.isUsable(authentication)")
public @interface RequiresUsableUser {
}
