package com.chalkak.backend.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 사용자의 식별자를 주입한다. 식별자를 얻는 방법은 {@link LoginUserArgumentResolver}에만 있다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}
