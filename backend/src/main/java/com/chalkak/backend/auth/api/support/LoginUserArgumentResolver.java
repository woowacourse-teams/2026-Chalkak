package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@Profile("!prod")
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public AuthenticatedUser resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        return new AuthenticatedUser(parseUserId(webRequest.getHeader(USER_ID_HEADER)));
    }

    private UUID parseUserId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
        }
        try {
            return UUID.fromString(headerValue);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
        }
    }
}