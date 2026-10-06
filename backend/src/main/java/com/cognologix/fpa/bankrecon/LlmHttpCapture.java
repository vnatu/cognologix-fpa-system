package com.cognologix.fpa.bankrecon;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Records the HTTP status and body for the current call. Request headers,
 * including Authorization, are not read and are not logged.
 */
final class LlmHttpCapture implements ClientHttpRequestInterceptor {

    record Snapshot(int status, String body) {}

    private static final ThreadLocal<Snapshot> LAST = new ThreadLocal<>();

    static void clear() {
        LAST.remove();
    }

    static Snapshot take() {
        Snapshot snapshot = LAST.get();
        LAST.remove();
        return snapshot;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        int status = 0;
        try {
            HttpStatusCode code = response.getStatusCode();
            if (code != null) {
                status = code.value();
            }
        } catch (Exception ignored) {
            status = 0;
        }
        byte[] payload = new byte[0];
        try (InputStream in = response.getBody()) {
            if (in != null) {
                payload = in.readAllBytes();
            }
        } catch (Exception ignored) {
            payload = new byte[0];
        }
        LAST.set(new Snapshot(status, new String(payload, StandardCharsets.UTF_8)));
        return new BufferedResponse(response, payload);
    }

    private static final class BufferedResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] payload;

        private BufferedResponse(ClientHttpResponse delegate, byte[] payload) {
            this.delegate = delegate;
            this.payload = payload == null ? new byte[0] : payload;
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(payload);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }
    }
}
