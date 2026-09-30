package com.chalkak.backend.common.logging;

public final class LogFields {

    public static final String REQUEST_ID = "requestId";
    public static final String TYPE = "type";
    public static final String METHOD = "method";
    public static final String ROUTE = "route";
    public static final String STATUS = "status";
    public static final String DURATION_MS = "durationMs";
    public static final String ERROR_CODE = "errorCode";
    public static final String EXCEPTION = "exception";
    public static final String HEAP_USED_BYTES = "heapUsedBytes";
    public static final String HEAP_MAX_BYTES = "heapMaxBytes";
    public static final String GC_TIME_MS_TOTAL = "gcTimeMsTotal";
    public static final String THREADS = "threads";
    public static final String HIKARI_ACTIVE = "hikariActive";
    public static final String HIKARI_PENDING = "hikariPending";

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_RUNTIME = "runtime";
    public static final String UNMATCHED_ROUTE = "UNMATCHED";

    private LogFields() {
    }
}
