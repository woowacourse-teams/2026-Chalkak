package com.chalkak.backend.common.logging;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 현재 요청의 requestId를 돌려준다. 감사 로그처럼 DB에 남기는 값을 접근·에러 로그와 같은 requestId로 잇기 위해 쓴다.
 *
 * <p>요청 밖(스케줄러 등)에서는 MDC에 값이 없으므로 새 UUID를 만들어 돌려준다. {@code Clock}처럼 주입받아 쓰면
 * 서비스가 MDC 같은 전역 상태를 직접 읽지 않는다.
 *
 * <p>MDC는 스레드에 묶여 있어 @Async나 별도 executor로 넘어가면 값이 전달되지 않는다. 요청 스레드 밖에서도 새 값을 만든다.
 */
@Component
public class CurrentRequestId {

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    public UUID resolve() {
        String requestId = MDC.get(LogFields.REQUEST_ID);
        if (requestId == null || !UUID_FORMAT.matcher(requestId).matches()) {
            return UUID.randomUUID();
        }

        return UUID.fromString(requestId);
    }
}
