package com.chalkak.backend.notification.service;

import java.util.Map;
import java.util.UUID;

public interface TopicPushTracing {

    Step startOccurrence(UUID eventId);

    Step startPublish();

    Step startProcessing(Map<String, String> headers);

    Step startFcmSend();

    interface Step extends AutoCloseable {

        void writeHeaders(Map<String, String> headers);

        void setEventId(UUID eventId);

        void recordOutcome(String outcome, boolean failed);

        @Override
        void close();
    }
}
