package com.chalkak.backend.notification.service;

import com.chalkak.backend.common.logging.Elapsed;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PushWorkerDbTiming {

    private final List<Map<String, Object>> transactions = new ArrayList<>();
    private long durationMs;

    public void recordTransaction(
            UUID deviceId,
            String operation,
            Elapsed elapsed,
            boolean completed
    ) {
        long transactionDurationMs = elapsed.toMillis();
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("deviceId", deviceId);
        transaction.put("operation", operation);
        transaction.put("transactionCompleted", completed);
        transaction.put("dbTransactionDurationMs", transactionDurationMs);
        transactions.add(Collections.unmodifiableMap(transaction));
        durationMs += transactionDurationMs;
    }

    public List<Map<String, Object>> getTransactions() {
        return List.copyOf(transactions);
    }

    public long getDurationMs() {
        return durationMs;
    }
}
