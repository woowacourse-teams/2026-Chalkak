package com.chalkak.backend.jev.infrastructure.infra;

import com.chalkak.backend.jev.service.JevJobPublisher;
import com.chalkak.backend.jev.service.JevJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.jev", name = "worker-enabled", havingValue = "true")
public class JevJobDispatcher {

    private final JevJobService jobs;
    private final JevJobPublisher publisher;

    @Scheduled(fixedDelay = 1000, scheduler = "jevTaskScheduler")
    public void dispatch() {
        for (var id : jobs.findDueRunIds()) {
            int attempt = 0;
            try {
                attempt = jobs.reserveDispatch(id);
                if (attempt != 0) {
                    // 외부 전송은 reserveDispatch의 짧은 트랜잭션이 끝난 후에 한다.
                    publisher.publish(id);
                    jobs.recordDispatch(
                            id,
                            attempt,
                            null
                    );
                }
            } catch (RuntimeException exception) {
                if (attempt != 0) {
                    jobs.recordDispatch(
                            id,
                            attempt,
                            exception.getClass().getSimpleName()
                    );
                }
                // 작업 행의 다음 시각이 남으므로 재시작/전송 실패도 다시 전달한다. 원문/키는 로그 금지.
                log.warn(
                        "Jev 작업 전달 실패 jobId={} errorType={}",
                        id,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
