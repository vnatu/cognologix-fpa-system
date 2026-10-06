package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.general.GeneralConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
            return OpenAiChatModel.builder()
                    .openAiApi(OpenAiApi.builder()
                            .baseUrl(DEFAULT_CHAT_URL)
                            .apiKey("not-needed")
                            .build())
                    .defaultOptions(chatOptions(DEFAULT_CHAT_MODEL))
                    .build();
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
                    .defaultOptions(OllamaOptions.builder().model(DEFAULT_OLLAMA_EMBED_MODEL).build())
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
                    .defaultOptions(OllamaOptions.builder()
                            .model(model)
                            .temperature(0.0)
                            .numPredict(2048)
                            .build())
                    .build();
        }
        String url = configService.getConfigValue(CFG_MLX_BASE_URL).orElse(DEFAULT_CHAT_URL);
        String model = configService.getConfigValue(CFG_MLX_CHAT_MODEL).orElse(DEFAULT_CHAT_MODEL);
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi(configService, url))
                .defaultOptions(chatOptions(model))
                .build();
    }

    static EmbeddingModel buildEmbeddingModel(GeneralConfigService configService) {
        if (ollamaProvider(configService)) {
            String url = configService.getConfigValue(CFG_EMBED_URL).orElse(DEFAULT_OLLAMA_URL);
            String model = configService.getConfigValue(CFG_EMBED_MODEL).orElse(DEFAULT_OLLAMA_EMBED_MODEL);
            return OllamaEmbeddingModel.builder()
                    .ollamaApi(ollamaApi(configService, url))
                    .defaultOptions(OllamaOptions.builder().model(model).build())
                    .build();
        }
        String url = configService.getConfigValue(CFG_MLX_BASE_URL).orElse(DEFAULT_CHAT_URL);
        String model = configService.getConfigValue(CFG_MLX_EMBED_MODEL).orElse(DEFAULT_OMLX_EMBED_MODEL);
        return new OpenAiEmbeddingModel(
                openAiApi(configService, url),
                MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(model).build());
    }

    static OpenAiChatOptions chatOptions(String chatModel) {
        return chatOptions(chatModel, 2048);
    }

    /**
     * Chat options do not send {@code enable_thinking=false}. The call log reports this value.
     */
    static final boolean THINKING_DISABLED = false;

    static OpenAiChatOptions chatOptions(String chatModel, int maxTokens) {
        return OpenAiChatOptions.builder()
                .model(chatModel)
                .temperature(0.0)
                .maxTokens(maxTokens)
                .build();
    }

    static String apiKey(GeneralConfigService configService) {
        String key = configService.getConfigValue(CFG_API_KEY).orElse("").trim();
        if (key.isEmpty()) {
            throw new IllegalStateException("mlx_api_key is required when llm_provider is OMLX");
        }
        return key;
    }

    private static OpenAiApi openAiApi(GeneralConfigService configService, String baseUrl) {
        return OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey(configService))
                .restClientBuilder(timedRestClient(configService))
                .build();
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
    private static final class NonStreamingChatInterceptor implements ClientHttpRequestInterceptor {
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
