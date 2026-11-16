package com.chalkak.backend.notification.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;

public final class TopicPushTracing {

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator
            .getInstance();
    private static final TopicPushTracing DISABLED = new TopicPushTracing(null);
    private static final TextMapGetter<Map<String, String>> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> headers) {
            return headers.keySet();
        }

        @Override
        public String get(Map<String, String> headers, String key) {
            return headers.get(key);
        }
    };

    private final Tracer tracer;

    public TopicPushTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    public static TopicPushTracing disabled() {
        return DISABLED;
    }

    public Step startOccurrence(UUID eventId) {
        if (tracer == null) {
            return Step.INACTIVE;
        }
        return new Step(tracer.spanBuilder("Run topic notification")
                .setNoParent()
                .setAttribute("eventId", eventId.toString())
                .startSpan());
    }

    public Step startPublish() {
        return startChild("Publish topic notification to SQS", SpanKind.PRODUCER);
    }

    public Step startProcessing(Map<String, String> headers) {
        if (tracer == null || headers.isEmpty()) {
            return Step.INACTIVE;
        }
        Context parent = PROPAGATOR.extract(
                Context.root(),
                headers,
                HEADER_GETTER
        );
        if (!Span.fromContext(parent).getSpanContext().isValid()) {
            return Step.INACTIVE;
        }
        return new Step(tracer.spanBuilder("Process topic notification from SQS")
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parent)
                .startSpan());
    }

    public Step startFcmSend() {
        return startChild("Send topic notification via FCM", SpanKind.CLIENT);
    }

    private Step startChild(String name, SpanKind kind) {
        if (tracer == null || !Span.current().isRecording()) {
            return Step.INACTIVE;
        }
        return new Step(tracer.spanBuilder(name).setSpanKind(kind).startSpan());
    }

    public static final class Step implements AutoCloseable {

        private static final Step INACTIVE = new Step(Span.getInvalid());
        private final Span span;
        private final Scope scope;
        private final String previousTraceId;
        private final String previousSpanId;

        private Step(Span span) {
            this.span = span;
            if (!span.isRecording()) {
                scope = Scope.noop();
                previousTraceId = null;
                previousSpanId = null;
                return;
            }
            scope = span.makeCurrent();
            previousTraceId = MDC.get("trace_id");
            previousSpanId = MDC.get("span_id");
            MDC.put("trace_id", span.getSpanContext().getTraceId());
            MDC.put("span_id", span.getSpanContext().getSpanId());
        }

        public void writeHeaders(Map<String, String> headers) {
            if (span.isRecording()) {
                PROPAGATOR.inject(
                        Context.current(),
                        headers,
                        Map::put
                );
            }
        }

        public void setEventId(UUID eventId) {
            if (span.isRecording() && eventId != null) {
                span.setAttribute("eventId", eventId.toString());
            }
        }

        public void recordOutcome(String outcome, boolean failed) {
            span.setAttribute("outcome", outcome);
            if (failed) {
                span.setStatus(StatusCode.ERROR);
            }
        }

        @Override
        public void close() {
            if (span.isRecording()) {
                scope.close();
                restoreMdc("trace_id", previousTraceId);
                restoreMdc("span_id", previousSpanId);
                span.end();
            }
        }

        private void restoreMdc(String key, String value) {
            if (value == null) {
                MDC.remove(key);
                return;
            }
            MDC.put(key, value);
        }
    }
}
