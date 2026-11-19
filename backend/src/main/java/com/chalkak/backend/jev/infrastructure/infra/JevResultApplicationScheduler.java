package com.chalkak.backend.jev.infrastructure.infra;

import com.chalkak.backend.jev.service.JevResultApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JevResultApplicationScheduler {

    private final JevResultApplicationService service;

    // 전송이 꺼져도 이미 받은 결과는 관리자 대기로 정리한다. 재시작 후 미적용 결과도 이 경로로 복구한다.
    @Scheduled(fixedDelay = 1000, scheduler = "jevTaskScheduler")
    public void apply() {
        for (var id : service.findUnappliedRunIds()) {
            try {
                service.apply(id);
            } catch (RuntimeException exception) {
                log.warn(
                        "Jev 결과 반영 실패 jobId={} errorType={}",
                        id,
                        exception.getClass().getSimpleName()
                );
            }
        }
    }
}
