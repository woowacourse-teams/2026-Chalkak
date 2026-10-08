package com.chalkak.backend.notification.infrastructure.infra;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// SDK의 재시도마다 기한을 확인하고 Android TTL도 남은 시간으로 다시 계산한다.
public class FcmHttpTransport extends HttpTransport {
    private final HttpClient client;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ThreadLocal<Instant> deadline = new ThreadLocal<>();

    public FcmHttpTransport(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void setDeadline(Instant expiresAt) {
        deadline.set(expiresAt);
    }

    public void clearDeadline() {
        deadline.remove();
    }

    @Override
    public void shutdown() {
        client.close();
    }

    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) {
        return new Request(method, url);
    }

    private class Request extends LowLevelHttpRequest {
        private final String method;
        private final URI address;
        private final List<Map.Entry<String, String>> headers = new ArrayList<>();

        Request(String method, String url) {
            this.method = method;
            this.address = URI.create(url);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.add(Map.entry(name, value));
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            Instant expiresAt = deadline.get();
            Duration timeout = findTimeout(expiresAt);
            byte[] body = createBody(expiresAt);
            HttpRequest.Builder builder = HttpRequest.newBuilder(address);
            builder.timeout(timeout);
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
            applyHeaders(builder);
            addContentHeaders(builder);
            return sendRequest(builder.build());
        }

        private Duration findTimeout(Instant expiresAt) throws IOException {
            Duration timeout = Duration.ofSeconds(10);
            if (expiresAt == null) {
                return timeout;
            }
            Duration remaining = Duration.between(clock.instant(), expiresAt);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new IOException("Push deadline exceeded");
            }
            if (remaining.compareTo(timeout) < 0) {
                return remaining;
            }
            return timeout;
        }

        private byte[] createBody(Instant expiresAt) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (getStreamingContent() != null) {
                getStreamingContent().writeTo(output);
            }
            return updateRemainingLifetime(output.toByteArray(), expiresAt);
        }

        private byte[] updateRemainingLifetime(byte[] body, Instant expiresAt) {
            // FirebaseMessaging의 JSON 요청은 gzip을 사용하지 않는다.
            String url = address.toString();
            if (expiresAt == null || !url.contains("/messages:send") || body.length == 0) {
                return body;
            }
            ObjectNode root = (ObjectNode) objectMapper.readTree(body);
            ObjectNode message = (ObjectNode) root.path("message");
            ObjectNode android = (ObjectNode) message.path("android");
            Duration remaining = Duration.between(clock.instant(), expiresAt);
            long remainingSeconds = Math.max(0, remaining.toSeconds());
            android.put("ttl", remainingSeconds + "s");
            return objectMapper.writeValueAsBytes(root);
        }

        private void applyHeaders(HttpRequest.Builder builder) {
            for (var header : headers) {
                applyHeader(builder, header);
            }
        }

        private void applyHeader(HttpRequest.Builder builder, Map.Entry<String, String> header) {
            if (header.getKey().equalsIgnoreCase("Content-Length")) {
                return;
            }
            builder.header(header.getKey(), header.getValue());
        }

        private void addContentHeaders(HttpRequest.Builder builder) {
            if (getContentType() != null) {
                builder.header("Content-Type", getContentType());
            }
            if (getContentEncoding() != null) {
                builder.header("Content-Encoding", getContentEncoding());
            }
        }

        private LowLevelHttpResponse sendRequest(HttpRequest request) throws IOException {
            try {
                return new Response(
                        client.send(request, HttpResponse.BodyHandlers.ofByteArray()));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Push request interrupted", exception);
            }
        }
    }

    private static class Response extends LowLevelHttpResponse {
        private final HttpResponse<byte[]> response;
        private final List<Map.Entry<String, String>> headers = new ArrayList<>();

        Response(HttpResponse<byte[]> response) {
            this.response = response;
            addHeaders(response.headers().map());
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(response.body());
        }
        @Override
        public String getContentEncoding() {
            return response.headers().firstValue("Content-Encoding").orElse(null);
        }
        @Override
        public long getContentLength() {
            return response.body().length;
        }
        @Override
        public String getContentType() {
            return response.headers().firstValue("Content-Type").orElse(null);
        }
        @Override
        public String getStatusLine() {
            return "HTTP " + response.statusCode();
        }
        @Override
        public int getStatusCode() {
            return response.statusCode();
        }
        @Override
        public String getReasonPhrase() {
            return "";
        }
        @Override
        public int getHeaderCount() {
            return headers.size();
        }
        @Override
        public String getHeaderName(int index) {
            var header = headers.get(index);
            return header.getKey();
        }
        @Override
        public String getHeaderValue(int index) {
            var header = headers.get(index);
            return header.getValue();
        }

        private void addHeaders(Map<String, List<String>> source) {
            for (var header : source.entrySet()) {
                addHeaderValues(header.getKey(), header.getValue());
            }
        }

        private void addHeaderValues(String name, List<String> values) {
            for (String value : values) {
                headers.add(Map.entry(name, value));
            }
        }
    }
}
