package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.common.logging.LogFields;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 접근 로그는 인증보다 먼저 실행되는 필터가 쓰므로 그 시점에는 로그인 사용자를 알 수 없다. 인증이 끝난 뒤 실행되는 이 인터셉터가 회원
 * 식별자를 요청 속성에 담아 두면 접근 로그가 꺼내 쓴다.
 *
 * <p>
 * 회원 토큰만 대상으로 한다. 관리자 토큰의 {@code sub}는 회원 식별자가 아니므로 남기지 않는다.
 */
@Component
public class AccessLogUserInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (!hasMemberScope()) {
            return true;
        }

        AuthenticatedUsers.find().ifPresent(user -> request.setAttribute(
                LogFields.USER_ID_REQUEST_ATTRIBUTE,
                user.userId().toString()));
        return true;
    }

    private boolean hasMemberScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AccessTokenScope.USER.toAuthority()::equals);
    }
}
