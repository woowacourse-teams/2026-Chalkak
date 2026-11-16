package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.TopicPushTracing;
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

public final class OpenTelemetryTopicPushTracing implements TopicPushTracing {

    private static final W3CTraceContextPropagator PROPAGATOR = W3CTraceContextPropagator
            .getInstance();
    private static final OpenTelemetryTopicPushTracing DISABLED = new OpenTelemetryTopicPushTracing(
            null);
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

    OpenTelemetryTopicPushTracing(Tracer tracer) {
        this.tracer = tracer;
    }

    public static OpenTelemetryTopicPushTracing disabled() {
        return DISABLED;
    }

    @Override
    public Step startOccurrence(UUID eventId) {
        if (tracer == null) {
            return SpanStep.INACTIVE;
        }
        return new SpanStep(tracer.spanBuilder("Run topic notification")
                .setNoParent()
                .setAttribute("eventId", eventId.toString())
                .startSpan());
    }

    @Override
    public Step startPublish() {
        return startChild("Publish topic notification to SQS", SpanKind.PRODUCER);
    }

    @Override
    public Step startProcessing(Map<String, String> headers) {
        if (tracer == null || headers.isEmpty()) {
            return SpanStep.INACTIVE;
        }
        Context parent = PROPAGATOR.extract(
                Context.root(),
                headers,
                HEADER_GETTER
        );
        if (!Span.fromContext(parent).getSpanContext().isValid()) {
            return SpanStep.INACTIVE;
        }
        return new SpanStep(tracer.spanBuilder("Process topic notification from SQS")
                .setSpanKind(SpanKind.CONSUMER)
                .setParent(parent)
                .startSpan());
    }

    @Override
    public Step startFcmSend() {
        return startChild("Send topic notification via FCM", SpanKind.CLIENT);
    }

    private SpanStep startChild(String name, SpanKind kind) {
        if (tracer == null || !Span.current().isRecording()) {
            return SpanStep.INACTIVE;
        }
        return new SpanStep(tracer.spanBuilder(name).setSpanKind(kind).startSpan());
    }

    private static final class SpanStep implements Step {

        private static final SpanStep INACTIVE = new SpanStep(Span.getInvalid());
        private final Span span;
        private final Scope scope;
        private final String previousTraceId;
        private final String previousSpanId;

        private SpanStep(Span span) {
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

        @Override
        public void writeHeaders(Map<String, String> headers) {
            if (span.isRecording()) {
                PROPAGATOR.inject(
                        Context.current(),
                        headers,
                        Map::put
                );
            }
        }

        @Override
        public void setEventId(UUID eventId) {
            if (span.isRecording() && eventId != null) {
                span.setAttribute("eventId", eventId.toString());
            }
        }

        @Override
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
