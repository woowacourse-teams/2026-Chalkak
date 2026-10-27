package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PushDeliveryLocksTest {
    @Test
    @DisplayName("같은 스레드의 중첩 진입도 거절해 외부 작업이 끝나기 전에 락이 제거되지 않는다")
    void tryAcquire_nestedSameKey_rejectsReentry() {
        // Given
        PushDeliveryLocks locks = new PushDeliveryLocks();
        UUID event = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        var held = locks.tryAcquire(event, user).orElseThrow();
        try {
            // When & Then
            assertThat(locks.tryAcquire(event, user)).isEmpty();
        } finally {
            locks.release(
                    event,
                    user,
                    held
            );
        }
        var next = locks.tryAcquire(event, user).orElseThrow();
        locks.release(
                event,
                user,
                next
        );
    }

    @Test
    @DisplayName("잘못된 키의 해제는 거절하고 실제 작업의 락은 유지한다")
    void release_differentKey_preservesOriginalLock() {
        // Given
        PushDeliveryLocks locks = new PushDeliveryLocks();
        UUID event = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        var held = locks.tryAcquire(event, user).orElseThrow();
        try {
            // When & Then
            assertThatThrownBy(() -> locks.release(
                    UUID.randomUUID(),
                    user,
                    held
            )).isInstanceOf(IllegalStateException.class);
            assertThat(locks.tryAcquire(event, user)).isEmpty();
        } finally {
            locks.release(
                    event,
                    user,
                    held
            );
        }
    }

    @Test
    @DisplayName("동시 획득과 해제·재획득이 반복돼도 같은 키의 임계 구역에는 한 스레드만 진입한다")
    void tryAcquire_repeatedConcurrentAcquisition_keepsOneOwnerAndRemovesIdleKey()
            throws Exception {
        // Given
        PushDeliveryLocks locks = new PushDeliveryLocks();
        UUID event = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger entered = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Runnable contender = () -> {
            try {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            for (int index = 0; index < 2000; index++) {
                var acquired = locks.tryAcquire(event, user);
                if (acquired.isEmpty()) {
                    continue;
                }
                try {
                    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                    entered.incrementAndGet();
                    Thread.yield();
                } finally {
                    active.decrementAndGet();
                    locks.release(
                            event,
                            user,
                            acquired.orElseThrow()
                    );
                }
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(contender);
            var second = pool.submit(contender);

            // When
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        // Then
        assertThat(entered.get()).isPositive();
        assertThat(maximum.get()).isEqualTo(1);
        assertThat(active.get()).isZero();
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(locks, "locks")).isEmpty();
    }
}
