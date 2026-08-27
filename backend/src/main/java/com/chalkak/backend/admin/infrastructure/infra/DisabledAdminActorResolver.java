package com.chalkak.backend.admin.infrastructure.infra;

import com.chalkak.backend.admin.api.support.AdminActorResolver;
import com.chalkak.backend.admin.api.support.AuthenticatedAdmin;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DisabledAdminActorResolver implements AdminActorResolver {

    @Override
    public AuthenticatedAdmin resolve() {
        throw new ForbiddenException(
                ErrorCode.FORBIDDEN,
                "관리자 API에 접근할 수 없습니다."
        );
    }
}
