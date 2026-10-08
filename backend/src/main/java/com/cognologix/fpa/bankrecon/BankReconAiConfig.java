package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.general.GeneralConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import com.openai.client.OpenAIClient;
import com.openai.core.Timeout;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FinSync model clients (ADR-065). {@code llm_provider} selects Ollama or oMLX.
 * Each call rebuilds the client from {@code general_config} so Settings changes apply
 * without restart.
 */
@Configuration
public class BankReconAiConfig {

    public static final String CFG_PROVIDER = "llm_provider";
    public static final String CFG_API_KEY = "mlx_api_key";
    public static final String CFG_MLX_BASE_URL = "mlx_base_url";
    public static final String CFG_MLX_CHAT_MODEL = "mlx_chat_model";
    public static final String CFG_MLX_EMBED_MODEL = "mlx_embedding_model";
    public static final String CFG_BASE_URL = "ollama_base_url";
    public static final String CFG_CHAT_MODEL = "ollama_chat_model";
    public static final String CFG_EMBED_URL = "ollama_embedding_url";
    public static final String CFG_EMBED_MODEL = "ollama_embedding_model";
    public static final String CFG_TIMEOUT = "ollama_request_timeout_seconds";

    public static final String PROVIDER_OLLAMA = "OLLAMA";
    public static final String PROVIDER_OMLX = "OMLX";

    public static final String DEFAULT_OLLAMA_URL = "http://localhost:11434";
    public static final String DEFAULT_OLLAMA_CHAT_MODEL = "qwen2.5:32b";
    public static final String DEFAULT_OLLAMA_EMBED_MODEL = "nomic-embed-text";
    public static final String DEFAULT_CHAT_URL = "http://localhost:9000";
    public static final String DEFAULT_CHAT_MODEL = "mlx-community/Qwen3.6-35B-A3B-4bit";
    public static final String DEFAULT_OMLX_EMBED_MODEL = "mlx-community/nomicai-modernbert-embed-base-4bit";

    @Bean("bankReconChatModel")
    @Primary
    public ChatModel bankReconChatModel(GeneralConfigService configService) {
        try {
            return buildChatModel(configService);
        } catch (Exception e) {
            return openAiChatModel(DEFAULT_CHAT_URL, DEFAULT_CHAT_MODEL, "not-needed",
                    Duration.ofSeconds(120), 2048);
        }
    }

    @Bean
    @Primary
    public EmbeddingModel bankReconEmbeddingModel(GeneralConfigService configService) {
        try {
            return buildEmbeddingModel(configService);
        } catch (Exception e) {
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(OllamaApi.builder().baseUrl(DEFAULT_OLLAMA_URL).build())
                    .options(OllamaEmbeddingOptions.builder().model(DEFAULT_OLLAMA_EMBED_MODEL).build())
                    .build();
        }
    }

    @Bean
    ChatClient bankReconChatClient(ChatModel bankReconChatModel) {
        return ChatClient.builder(bankReconChatModel).build();
    }

    static boolean ollamaProvider(GeneralConfigService configService) {
        String provider = configService.getConfigValue(CFG_PROVIDER).orElse(PROVIDER_OMLX).trim();
        return PROVIDER_OLLAMA.equalsIgnoreCase(provider);
    }

    static ChatModel buildChatModel(GeneralConfigService configService) {
        if (ollamaProvider(configService)) {
            String url = configService.getConfigValue(CFG_BASE_URL).orElse(DEFAULT_OLLAMA_URL);
            String model = configService.getConfigValue(CFG_CHAT_MODEL).orElse(DEFAULT_OLLAMA_CHAT_MODEL);
            return OllamaChatModel.builder()
                    .ollamaApi(ollamaApi(configService, url))
                    .options(OllamaChatOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .numPredict(2048)
                            .build())
                    .build();
        }
        String url = configService.getConfigValue(CFG_MLX_BASE_URL).orElse(DEFAULT_CHAT_URL);
        String model = configService.getConfigValue(CFG_MLX_CHAT_MODEL).orElse(DEFAULT_CHAT_MODEL);
        return openAiChatModel(url, model, apiKey(configService), readTimeout(configService), 2048);
    }

    static EmbeddingModel buildEmbeddingModel(GeneralConfigService configService) {
        if (ollamaProvider(configService)) {
            String url = configService.getConfigValue(CFG_EMBED_URL).orElse(DEFAULT_OLLAMA_URL);
            String model = configService.getConfigValue(CFG_EMBED_MODEL).orElse(DEFAULT_OLLAMA_EMBED_MODEL);
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi(configService, url))
                    .options(OllamaEmbeddingOptions.builder().model(model).build())
                    .build();
        }
        String url = configService.getConfigValue(CFG_MLX_BASE_URL).orElse(DEFAULT_CHAT_URL);
        String model = configService.getConfigValue(CFG_MLX_EMBED_MODEL).orElse(DEFAULT_OMLX_EMBED_MODEL);
        return openAiEmbeddingModel(url, model, apiKey(configService), readTimeout(configService));
    }

    /**
     * Chat options do not send {@code enable_thinking=false}. The call log reports this value.
     */
    static final boolean THINKING_DISABLED = false;

    static String apiKey(GeneralConfigService configService) {
        String key = configService.getConfigValue(CFG_API_KEY).orElse("").trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("mlx_api_key is required when llm_provider is OMLX");
        }
        return key;
    }

    /**
     * Spring AI 1.0 appended {@code /v1/chat/completions} and {@code /v1/embeddings} to the
     * configured base URL. The OpenAI Java SDK treats the base URL as the {@code /v1} root.
     */
    static String openAiSdkBaseUrl(String configured) {
        String trimmed = configured == null ? "" : configured.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed;
        }
        return trimmed + "/v1";
    }

    static Duration readTimeout(GeneralConfigService configService) {
        int seconds = parseInt(configService.getConfigValue(CFG_TIMEOUT).orElse("120"), 120);
        if (seconds <= 0) {
            seconds = 120;
        }
        return Duration.ofSeconds(seconds);
    }

    static ChatModel openAiChatModel(
            String configuredBaseUrl, String model, String key, Duration readTimeout, int maxTokens) {
        OpenAiHttpClientBuilderCustomizer customizer = openAiCustomizer(readTimeout);
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(openAiSdkBaseUrl(configuredBaseUrl))
                .apiKey(key)
                .model(model)
                .temperature(0.0)
                .maxTokens(maxTokens)
                .timeout(readTimeout)
                .maxRetries(0)
                .build();
        return OpenAiChatModel.builder()
                .openAiClient(openAiClient(options.getBaseUrl(), options.getApiKey(), options.getModel(),
                        options.getTimeout(), options.getMaxRetries(), customizer))
                .options(options)
                .httpClientBuilderCustomizer(customizer)
                .build();
    }

    static EmbeddingModel openAiEmbeddingModel(
            String configuredBaseUrl, String model, String key, Duration readTimeout) {
        OpenAiHttpClientBuilderCustomizer customizer = openAiCustomizer(readTimeout);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .baseUrl(openAiSdkBaseUrl(configuredBaseUrl))
                .apiKey(key)
                .model(model)
                .timeout(readTimeout)
                .maxRetries(0)
                .build();
        return OpenAiEmbeddingModel.builder()
                .openAiClient(openAiClient(options.getBaseUrl(), options.getApiKey(), options.getModel(),
                        options.getTimeout(), options.getMaxRetries(), customizer))
                .metadataMode(MetadataMode.EMBED)
                .options(options)
                .httpClientBuilderCustomizer(customizer)
                .build();
    }

    private static OpenAIClient openAiClient(
            String sdkBaseUrl,
            String key,
            String model,
            Duration readTimeout,
            int maxRetries,
            OpenAiHttpClientBuilderCustomizer customizer) {
        return OpenAiSetup.setupSyncClient(
                sdkBaseUrl,
                key,
                null,
                null,
                null,
                null,
                false,
                false,
                model,
                readTimeout,
                maxRetries,
                null,
                null,
                ObservationRegistry.NOOP,
                null,
                List.of(customizer));
    }

    /**
     * Connect is 10 seconds, so a dead oMLX host does not hold the call for a minute.
     * {@code RequestOptions.timeout(Duration)} leaves connect unset, and {@code Timeout.connect()}
     * then defaults to one minute and replaces the builder timeout on each call.
     * {@code withConnectTimeout} puts the 10 second limit on the call itself.
     * The read timeout is the configured request timeout.
     * The interceptor records the response body for the redacted call log.
     */
    static OpenAiHttpClientBuilderCustomizer openAiCustomizer(Duration readTimeout) {
        long seconds = readTimeout == null ? 120 : readTimeout.getSeconds();
        Duration connect = Duration.ofSeconds(10);
        Duration read = Duration.ofSeconds(Math.max(seconds, 1));
        return builder -> builder
                .timeout(Timeout.builder().connect(connect).read(read).write(read).request(read).build())
                .interceptor(chain -> {
                    Response response = chain.withConnectTimeout(10, TimeUnit.SECONDS).proceed(chain.request());
                    ResponseBody responseBody = response.body();
                    byte[] bytes = responseBody == null ? new byte[0] : responseBody.bytes();
                    okhttp3.MediaType mediaType = responseBody == null ? null : responseBody.contentType();
                    LlmHttpCapture.record(response.code(), new String(bytes, StandardCharsets.UTF_8));
                    return response.newBuilder().body(ResponseBody.create(bytes, mediaType)).build();
                });
    }

    private static OllamaApi ollamaApi(GeneralConfigService configService, String baseUrl) {
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter();
        json.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.TEXT_PLAIN));
        RestClient.Builder restClientBuilder = timedRestClient(configService)
                .messageConverters(converters -> converters.add(0, json))
                .requestInterceptor(new NonStreamingChatInterceptor());
        return OllamaApi.builder().baseUrl(baseUrl).restClientBuilder(restClientBuilder).build();
    }

    private static RestClient.Builder timedRestClient(GeneralConfigService generalConfigService) {
        int timeout = parseInt(generalConfigService.getConfigValue(CFG_TIMEOUT).orElse("120"), 120);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new LlmHttpCapture());
    }

    /**
     * Ollama {@code /api/chat} defaults to NDJSON streaming ({@code application/octet-stream})
     * unless {@code stream: false} is present. Spring AI 1.0 {@code ChatClient.call()} then
     * fails extracting {@code OllamaApi$ChatResponse}.
     */
    static final class NonStreamingChatInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(
                HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
            byte[] outgoing = forceChatStreamFalse(request, body);
            return new JsonContentTypeResponse(execution.execute(request, outgoing));
        }

        private static byte[] forceChatStreamFalse(HttpRequest request, byte[] body) {
            if (body == null || body.length == 0) {
                return body;
            }
            String path = request.getURI().getPath();
            if (path == null || !path.endsWith("/api/chat")) {
                return body;
            }
            String json = new String(body, StandardCharsets.UTF_8);
            String next;
            if (json.contains("\"stream\"")) {
                next = json.replaceAll("\"stream\"\\s*:\\s*true", "\"stream\":false");
            } else {
                next = json.replaceFirst("\\{", "{\"stream\":false,");
            }
            if (next.equals(json)) {
                return body;
            }
            byte[] updated = next.getBytes(StandardCharsets.UTF_8);
            request.getHeaders().setContentLength(updated.length);
            return updated;
        }
    }

    private static final class JsonContentTypeResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;

        private JsonContentTypeResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
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
        public InputStream getBody() throws IOException {
            return delegate.getBody();
        }

        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(delegate.getHeaders());
            MediaType contentType = headers.getContentType();
            if (contentType != null && MediaType.APPLICATION_OCTET_STREAM.includes(contentType)) {
                headers.setContentType(MediaType.APPLICATION_JSON);
            }
            return headers;
        }
    }

    static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}

interface StructuredLlmClient {
    LlmBatchResult mapBatch(String prompt, int maxTokens);
}

interface NarrationEmbedder {
    float[] embed(String text);
}
