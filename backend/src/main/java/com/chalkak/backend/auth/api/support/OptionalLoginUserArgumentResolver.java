package com.chalkak.backend.auth.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class OptionalLoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final Environment environment;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(OptionalLoginUser.class)
                && Optional.class.equals(parameter.getParameterType());
    }

    @Override
    public Optional<AuthenticatedUser> resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            return Optional.empty();
        }

        String headerValue = webRequest.getHeader(USER_ID_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new AuthenticatedUser(UUID.fromString(headerValue)));
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "유효하지 않은 인증 정보입니다."
            );
        }
    }
}
