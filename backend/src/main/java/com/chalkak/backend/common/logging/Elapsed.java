package com.chalkak.backend.common.logging;

import java.util.concurrent.TimeUnit;

public final class Elapsed {

    private final long startedAtNanos;

    private Elapsed(long startedAtNanos) {
        this.startedAtNanos = startedAtNanos;
    }

    public static Elapsed start() {
        return new Elapsed(System.nanoTime());
    }

    public long toMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
