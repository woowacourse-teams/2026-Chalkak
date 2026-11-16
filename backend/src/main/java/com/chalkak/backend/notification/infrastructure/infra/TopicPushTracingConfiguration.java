package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.TopicPushTracing;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Slf4j
class TopicPushTracingConfiguration {

    @Bean(destroyMethod = "close")
    @Profile("dev | prod")
    SdkTracerProvider topicPushTracerProvider() {
        return SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(new NotificationSpanExporter())
                        .setMaxQueueSize(2048)
                        .build())
                .build();
    }

    @Bean
    TopicPushTracing topicPushTracing(
            @Qualifier("topicPushTracerProvider") ObjectProvider<SdkTracerProvider> providers) {
        SdkTracerProvider provider = providers.getIfAvailable();
        if (provider == null) {
            return OpenTelemetryTopicPushTracing.disabled();
        }
        return new OpenTelemetryTopicPushTracing(provider.get("chalkak-topic-push"));
    }

    private static final class NotificationSpanExporter implements SpanExporter {

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            for (SpanData span : spans) {
                var entry = log.atInfo().addKeyValue("type", "notification")
                        .addKeyValue("stage", "topic_trace_span")
                        .addKeyValue("trace_id", span.getTraceId())
                        .addKeyValue("span_id", span.getSpanId())
                        .addKeyValue("parent_span_id", span.getParentSpanId())
                        .addKeyValue("spanName", span.getName())
                        .addKeyValue("startedAtEpochMs",
                                TimeUnit.NANOSECONDS.toMillis(span.getStartEpochNanos()))
                        .addKeyValue("durationMs",
                                (span.getEndEpochNanos() - span.getStartEpochNanos()) / 1_000_000.0)
                        .addKeyValue("spanStatus", span.getStatus().getStatusCode());
                span.getAttributes()
                        .forEach((key, value) -> entry.addKeyValue(key.getKey(), value));
                entry.log("주제 푸시 추적 구간");
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
