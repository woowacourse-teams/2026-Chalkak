package com.chalkak.backend.admin.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisabledAdminActorResolverTest {

    private final DisabledAdminActorResolver disabledAdminActorResolver =
            new DisabledAdminActorResolver();

    @Test
    @DisplayName("실제 관리자 인증이 연결되기 전 운영 환경의 작업자 조회를 거부한다")
    void resolve_beforeProductionAuthentication_throwsForbiddenException() {
        assertThatThrownBy(disabledAdminActorResolver::resolve)
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("관리자 API에 접근할 수 없습니다.")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
