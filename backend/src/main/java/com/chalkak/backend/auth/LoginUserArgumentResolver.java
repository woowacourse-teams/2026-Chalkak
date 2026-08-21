package com.chalkak.backend.auth;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Spring Security 도입 전까지 쓰는 임시 수단이다. 요청 헤더 값을 검증 없이 신뢰하므로 누구나 남의 계정을 조작할 수 있다.
 * {@link AuthWebConfig}가 등록 대상 환경을 제한한다.
 *
 * <p>Security 도입 시 이 클래스의 내부만 SecurityContext 조회로 교체하면 컨트롤러 아래는 바뀌지 않는다.
 */
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String userId = webRequest.getHeader(USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, USER_ID_HEADER + " 헤더가 필요합니다.");
        }
        return toUuid(userId);
    }

    private UUID toUuid(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    USER_ID_HEADER + " 헤더 형식이 올바르지 않습니다."
            );
        }
    }
}
