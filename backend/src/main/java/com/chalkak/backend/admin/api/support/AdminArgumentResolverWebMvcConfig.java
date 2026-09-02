package com.chalkak.backend.admin.api.support;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class AdminArgumentResolverWebMvcConfig implements WebMvcConfigurer {

    private static final AdminActorResolver DENY_ALL_ADMIN_ACTOR_RESOLVER = () -> {
        throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "관리자 API에 접근할 수 없습니다."
        );
    };

    private final ObjectProvider<AdminActorResolver> adminActorResolverProvider;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        AdminActorResolver adminActorResolver = adminActorResolverProvider.getIfAvailable(
                () -> DENY_ALL_ADMIN_ACTOR_RESOLVER
        );
        resolvers.add(new CurrentAdminArgumentResolver(adminActorResolver));
    }
}
