package com.chalkak.backend.notification.service;

import com.chalkak.backend.common.logging.Elapsed;

public final class DevicePushTiming {

    private Long requestDurationMs;
    private Boolean sdkAccepted;

    public void recordRequest(Elapsed elapsed, boolean accepted) {
        requestDurationMs = elapsed.toMillis();
        sdkAccepted = accepted;
    }

    public boolean hasRequest() {
        return requestDurationMs != null;
    }

    public Long getRequestDurationMs() {
        return requestDurationMs;
    }

    public Boolean getSdkAccepted() {
        return sdkAccepted;
    }
}
