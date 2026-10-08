package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.general.GeneralConfigService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Request;
import okio.Buffer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks the Spring AI 2.0.1 OpenAI client against a local HTTP server.
 * Nothing here calls a configured model or the dev database.
 */
class OpenAiPathCapabilityTest {

    private static final String CHAT_JSON = """
            {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"m",
             "choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
            """;

    private static final String EMBED_JSON = """
            {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.25,0.5]}],
             "model":"embed","usage":{"prompt_tokens":1,"total_tokens":1}}
            """;

    @Test
    void extraBodyFieldIsSentOnTheWire() throws Exception {
        try (Probe probe = Probe.start(CHAT_JSON)) {
            String key = "sk-extra-body-secret";
            ChatModel model = chatModel(probe.baseUrl(), "extra-model", key, Duration.ofSeconds(10),
                    Map.of("chat_template_kwargs", Map.of("enable_thinking", false)));
            call(model, "extra-model");

            Hit hit = probe.onlyHit();
            assertThat(hit.path).isEqualTo("/v1/chat/completions");
            assertThat(hit.body).contains("chat_template_kwargs");
            assertThat(hit.body).contains("enable_thinking");
            assertThat(hit.body).contains("false");
        }
    }

    @Test
    void nonStreamingCallDoesNotSendStreamTrue() throws Exception {
        try (Probe probe = Probe.start(CHAT_JSON)) {
            ChatModel model = BankReconAiConfig.openAiChatModel(
                    probe.baseUrl(), "stream-model", "sk-stream-secret", Duration.ofSeconds(10), 32);
            call(model, "stream-model");

            Hit hit = probe.onlyHit();
            assertThat(hit.body).doesNotContain("\"stream\"");
        }
    }

    @Test
    void extraBodyCanForceStreamFalse() throws Exception {
        try (Probe probe = Probe.start(CHAT_JSON)) {
            ChatModel model = chatModel(probe.baseUrl(), "force-stream", "sk-force-stream", Duration.ofSeconds(10),
                    Map.of("stream", false));
            call(model, "force-stream");
            assertThat(probe.onlyHit().body).contains("\"stream\":false");
        }
    }

    @Test
    void interceptorSeesRequestAndResponseAndRedactsTheApiKey() throws Exception {
        String key = "sk-redact-this-key-value";
        AtomicReference<String> requestBody = new AtomicReference<>();
        OpenAiHttpClientBuilderCustomizer capture = builder -> builder.interceptor(chain -> {
            Request request = chain.request();
            Buffer buffer = new Buffer();
            if (request.body() != null) {
                request.body().writeTo(buffer);
            }
            byte[] bytes = buffer.readByteArray();
            requestBody.set(new String(bytes, StandardCharsets.UTF_8));
            Request replay = request.newBuilder()
                    .method(request.method(), okhttp3.RequestBody.create(bytes, request.body().contentType()))
                    .build();
            return chain.proceed(replay);
        });
        try (Probe probe = Probe.start(CHAT_JSON)) {
            ChatModel model = chatModel(probe.baseUrl(), "redact-model", key, Duration.ofSeconds(10),
                    Map.of(), capture);
            call(model, "redact-model");

            Hit hit = probe.onlyHit();
            assertThat(hit.authorization).isEqualTo("Bearer " + key);
            assertThat(requestBody.get()).isEqualTo(hit.body);
            LlmHttpCapture.Snapshot response = LlmHttpCapture.take();
            assertThat(response).isNotNull();
            assertThat(response.body()).contains("chatcmpl-test");

            String logged = LlmTrace.redact(
                    "authorization: " + hit.authorization + "\nrequest:\n" + requestBody.get()
                            + "\nresponse:\n" + response.body(),
                    key);
            assertThat(logged).doesNotContain(key);
            assertThat(logged).contains("chatcmpl-test");
            assertThat(logged).contains("[redacted]");
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.SECONDS)
    void readTimeoutAbortsADelayedResponse() throws Exception {
        try (Probe probe = Probe.start(CHAT_JSON)) {
            probe.delayMs = 5_000;
            ChatModel model = BankReconAiConfig.openAiChatModel(
                    probe.baseUrl(), "slow-model", "sk-timeout", Duration.ofSeconds(1), 16);
            long started = System.nanoTime();
            Throwable failure = null;
            try {
                call(model, "slow-model");
            } catch (RuntimeException e) {
                failure = e;
            }
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertThat(failure).isNotNull();
            assertThat(elapsedMs).isLessThan(4_000);
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void connectTimeoutAbortsAnUnreachableHost() {
        ChatModel model = BankReconAiConfig.openAiChatModel(
                "http://192.0.2.1:9", "dead-model", "sk-connect", Duration.ofSeconds(120), 16);
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            call(model, "dead-model");
        } catch (RuntimeException e) {
            failure = e;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assertThat(failure).isNotNull();
        assertThat(elapsedMs).isLessThan(15_000);
    }

    @Test
    void embeddingsUseCustomBaseUrlAndApiKey() throws Exception {
        String key = "sk-embed-key";
        try (Probe probe = Probe.start(EMBED_JSON)) {
            GeneralConfigService config = config(Map.of(
                    BankReconAiConfig.CFG_PROVIDER, BankReconAiConfig.PROVIDER_OMLX,
                    BankReconAiConfig.CFG_API_KEY, key,
                    BankReconAiConfig.CFG_MLX_BASE_URL, probe.baseUrl(),
                    BankReconAiConfig.CFG_MLX_EMBED_MODEL, "embed-model",
                    BankReconAiConfig.CFG_TIMEOUT, "10"));
            EmbeddingModel model = BankReconAiConfig.buildEmbeddingModel(config);
            float[] vector = model.embed("narration");

            Hit hit = probe.onlyHit();
            assertThat(hit.path).isEqualTo("/v1/embeddings");
            assertThat(hit.authorization).isEqualTo("Bearer " + key);
            assertThat(hit.body).contains("embed-model");
            assertThat(vector).containsExactly(0.25f, 0.5f);
        }
    }

    @Test
    void baseUrlModelAndKeyChangeWithoutRestart() throws Exception {
        try (Probe first = Probe.start(CHAT_JSON); Probe second = Probe.start(CHAT_JSON)) {
            GeneralConfigService config = config(Map.of(
                    BankReconAiConfig.CFG_PROVIDER, BankReconAiConfig.PROVIDER_OMLX,
                    BankReconAiConfig.CFG_API_KEY, "sk-first-key",
                    BankReconAiConfig.CFG_MLX_BASE_URL, first.baseUrl(),
                    BankReconAiConfig.CFG_MLX_CHAT_MODEL, "model-first",
                    BankReconAiConfig.CFG_TIMEOUT, "10"));
            call(BankReconAiConfig.buildChatModel(config), "model-first");

            when(config.getConfigValue(anyString())).thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                return Optional.ofNullable(Map.of(
                        BankReconAiConfig.CFG_PROVIDER, BankReconAiConfig.PROVIDER_OMLX,
                        BankReconAiConfig.CFG_API_KEY, "sk-second-key",
                        BankReconAiConfig.CFG_MLX_BASE_URL, second.baseUrl(),
                        BankReconAiConfig.CFG_MLX_CHAT_MODEL, "model-second",
                        BankReconAiConfig.CFG_TIMEOUT, "10").get(key));
            });
            call(BankReconAiConfig.buildChatModel(config), "model-second");

            assertThat(first.onlyHit().authorization).isEqualTo("Bearer sk-first-key");
            assertThat(first.onlyHit().body).contains("model-first");
            assertThat(second.onlyHit().authorization).isEqualTo("Bearer sk-second-key");
            assertThat(second.onlyHit().body).contains("model-second");
        }
    }

    private static void call(ChatModel model, String modelName) {
        ChatClient.builder(model).build()
                .prompt()
                .user("ping")
                .options(OpenAiChatOptions.builder().model(modelName).temperature(0.0).maxTokens(16))
                .call()
                .chatResponse();
    }

    private static ChatModel chatModel(
            String baseUrl, String model, String key, Duration timeout, Map<String, Object> extraBody) {
        return chatModel(baseUrl, model, key, timeout, extraBody, builder -> {
        });
    }

    private static ChatModel chatModel(
            String baseUrl,
            String model,
            String key,
            Duration timeout,
            Map<String, Object> extraBody,
            OpenAiHttpClientBuilderCustomizer extra) {
        OpenAiHttpClientBuilderCustomizer customizer = BankReconAiConfig.openAiCustomizer(timeout);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(BankReconAiConfig.openAiSdkBaseUrl(baseUrl))
                .apiKey(key)
                .model(model)
                .temperature(0.0)
                .maxTokens(32)
                .timeout(timeout)
                .maxRetries(0)
                .extraBody(extraBody.isEmpty() ? null : extraBody)
                .build();
        var client = OpenAiSetup.setupSyncClient(
                options.getBaseUrl(), options.getApiKey(), null, null, null, null, false, false,
                options.getModel(), options.getTimeout(), options.getMaxRetries(), null, null,
                ObservationRegistry.NOOP, null, List.of(customizer, extra));
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(options)
                .httpClientBuilderCustomizer(customizer)
                .httpClientBuilderCustomizer(extra)
                .build();
    }

    private static GeneralConfigService config(Map<String, String> values) {
        GeneralConfigService config = mock(GeneralConfigService.class);
        when(config.getConfigValue(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(values.get(invocation.getArgument(0))));
        return config;
    }

    private static final class Probe implements AutoCloseable {
        private final HttpServer server;
        private final List<Hit> hits = new CopyOnWriteArrayList<>();
        private volatile long delayMs;

        private Probe(HttpServer server) {
            this.server = server;
        }

        static Probe start(String responseJson) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            Probe probe = new Probe(server);
            byte[] payload = responseJson.getBytes(StandardCharsets.UTF_8);
            server.createContext("/", exchange -> probe.handle(exchange, payload));
            server.start();
            return probe;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        Hit onlyHit() {
            assertThat(hits).hasSize(1);
            return hits.get(0);
        }

        private void handle(HttpExchange exchange, byte[] payload) throws IOException {
            byte[] request = exchange.getRequestBody().readAllBytes();
            hits.add(new Hit(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(request, StandardCharsets.UTF_8)));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private record Hit(String path, String authorization, String body) {}
}
