package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
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
    @DisplayName("같은 스레드의 중첩 진입을 거절하고 해제 후에는 다시 등록한다")
    void tryAcquire_nestedSameKey_rejectsReentryUntilReleased() {
        // Given
        PushDeliveryLocks locks = new PushDeliveryLocks();
        UUID event = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        assertThat(locks.tryAcquire(event, user)).isTrue();
        try {
            // When & Then
            assertThat(locks.tryAcquire(event, user)).isFalse();
        } finally {
            locks.release(event, user);
        }
        assertThat(locks.tryAcquire(event, user)).isTrue();
        locks.release(event, user);
    }

    @Test
    @DisplayName("사건이나 회원이 다르면 동시에 등록하고 각 키를 독립적으로 제거한다")
    void tryAcquire_differentKeys_keepsIndependentRegistrations() {
        // Given
        PushDeliveryLocks locks = new PushDeliveryLocks();
        UUID event = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        UUID otherEvent = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        assertThat(locks.tryAcquire(event, user)).isTrue();

        // When
        assertThat(locks.tryAcquire(otherEvent, user)).isTrue();
        assertThat(locks.tryAcquire(event, otherUser)).isTrue();
        locks.release(otherEvent, user);

        // Then
        assertThat(locks.tryAcquire(event, user)).isFalse();
        assertThat(locks.tryAcquire(event, otherUser)).isFalse();
        locks.release(event, user);
        locks.release(event, otherUser);
        assertThat((Set<?>) ReflectionTestUtils.getField(locks, "inProgress")).isEmpty();
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
                if (!locks.tryAcquire(event, user)) {
                    continue;
                }
                try {
                    maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                    entered.incrementAndGet();
                    Thread.yield();
                } finally {
                    active.decrementAndGet();
                    locks.release(event, user);
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
        assertThat((Set<?>) ReflectionTestUtils.getField(locks, "inProgress")).isEmpty();
    }
}
