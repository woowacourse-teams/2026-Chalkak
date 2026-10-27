package com.chalkak.backend.notification.service;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// 하나의 PushDeliveryService를 공유하는 JVM 안에서만 유효하다.
final class PushDeliveryLocks {
    private final Set<DeliveryKey> inProgress = ConcurrentHashMap.newKeySet();

    boolean tryAcquire(UUID eventId, UUID userId) {
        return inProgress.add(new DeliveryKey(eventId, userId));
    }

    // 등록에 성공한 처리만 finally에서 자신의 키를 제거한다.
    void release(UUID eventId, UUID userId) {
        inProgress.remove(new DeliveryKey(eventId, userId));
    }

    private record DeliveryKey(UUID eventId, UUID userId) {
    }
}
