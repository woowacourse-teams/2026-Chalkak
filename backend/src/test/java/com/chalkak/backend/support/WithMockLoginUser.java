package com.chalkak.backend.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

/**
 * 액세스 토큰을 실제로 발급하지 않고 인증된 요청을 만든다. 토큰 문자열을 만드는 경로는
 * {@code SecurityFilterChainTest}가 따로 검증하므로, 컨트롤러 테스트는 인증 이후만 다룬다.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WithSecurityContext(factory = WithMockLoginUserSecurityContextFactory.class)
public @interface WithMockLoginUser {

    /** 로그인 사용자의 식별자. 비워 두면 임의의 값을 사용한다. */
    String value() default "";
}
