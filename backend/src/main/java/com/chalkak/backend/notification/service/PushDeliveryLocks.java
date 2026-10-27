package com.chalkak.backend.notification.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

// 하나의 PushDeliveryService를 공유하는 JVM 안에서만 유효하다.
final class PushDeliveryLocks {
    private final Map<DeliveryKey, ReentrantLock> locks = new HashMap<>();

    synchronized Optional<ReentrantLock> tryAcquire(UUID eventId, UUID userId) {
        DeliveryKey key = new DeliveryKey(eventId, userId);
        ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
        if (lock.isHeldByCurrentThread() || !lock.tryLock()) {
            return Optional.empty();
        }
        return Optional.of(lock);
    }

    synchronized void release(
            UUID eventId,
            UUID userId,
            ReentrantLock lock
    ) {
        DeliveryKey key = new DeliveryKey(eventId, userId);
        if (locks.get(key) != lock) {
            throw new IllegalStateException("현재 작업의 푸시 락이 아닙니다.");
        }
        // 획득과 해제·제거를 같은 모니터로 보호해 사용 중인 키에 다른 락이 생기지 않게 한다.
        lock.unlock();
        locks.remove(key);
    }

    private record DeliveryKey(UUID eventId, UUID userId) {
    }
}
