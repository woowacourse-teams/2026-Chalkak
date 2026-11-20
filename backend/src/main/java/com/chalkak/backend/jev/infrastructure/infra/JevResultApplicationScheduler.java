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

    // 전송이 꺼지면 미완료 운영 작업도 닫는다. 이미지 준비 후 실제 반영/관리자 알림을 이어서 처리한다.
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
