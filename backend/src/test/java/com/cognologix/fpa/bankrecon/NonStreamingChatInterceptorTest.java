package com.cognologix.fpa.bankrecon;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class NonStreamingChatInterceptorTest {

    private final BankReconAiConfig.NonStreamingChatInterceptor interceptor =
            new BankReconAiConfig.NonStreamingChatInterceptor();

    @Test
    void chatRequestWithoutStreamFlagInsertsStreamFalseAndRewritesOctetStream() throws Exception {
        MockClientHttpRequest request = chatRequest("/api/chat");
        byte[] body = "{\"model\":\"qwen2.5:32b\"}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        ClientHttpResponse response = interceptor.intercept(request, body, capture(sent, MediaType.APPLICATION_OCTET_STREAM));

        assertThat(new String(sent.get(), StandardCharsets.UTF_8))
                .isEqualTo("{\"stream\":false,\"model\":\"qwen2.5:32b\"}");
        assertThat(request.getHeaders().getContentLength()).isEqualTo(sent.get().length);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void chatRequestWithStreamTrueIsForcedFalse() throws Exception {
        MockClientHttpRequest request = chatRequest("/v1/api/chat");
        byte[] body = "{\"stream\": true, \"model\":\"qwen\"}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        interceptor.intercept(request, body, capture(sent, MediaType.APPLICATION_JSON));

        assertThat(new String(sent.get(), StandardCharsets.UTF_8))
                .isEqualTo("{\"stream\":false, \"model\":\"qwen\"}");
    }

    @Test
    void nonChatPathAndEmptyBodyAreLeftUnchanged() throws Exception {
        MockClientHttpRequest embed = chatRequest("/api/embed");
        byte[] body = "{\"model\":\"nomic-embed-text\"}".getBytes(StandardCharsets.UTF_8);
        AtomicReference<byte[]> sent = new AtomicReference<>();

        ClientHttpResponse response = interceptor.intercept(embed, body, capture(sent, MediaType.APPLICATION_JSON));

        assertThat(sent.get()).isSameAs(body);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        MockClientHttpRequest chat = chatRequest("/api/chat");
        AtomicReference<byte[]> emptySent = new AtomicReference<>();
        interceptor.intercept(chat, new byte[0], capture(emptySent, MediaType.APPLICATION_JSON));
        assertThat(emptySent.get()).isEmpty();
    }

    private static MockClientHttpRequest chatRequest(String path) {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("http://localhost:11434" + path));
    }

    private static ClientHttpRequestExecution capture(AtomicReference<byte[]> sent, MediaType contentType) {
        return (request, body) -> {
            sent.set(body);
            MockClientHttpResponse response = new MockClientHttpResponse("{}".getBytes(StandardCharsets.UTF_8), HttpStatus.OK);
            response.getHeaders().setContentType(contentType);
            return response;
        };
    }
}
