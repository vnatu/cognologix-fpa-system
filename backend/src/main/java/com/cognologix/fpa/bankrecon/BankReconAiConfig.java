package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.dto.BankReconDtos.BatchMappingResponse;
import com.cognologix.fpa.general.GeneralConfigService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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
 * Ollama beans for FinSync (ADR-065). Startup beans read {@code general_config};
 * {@link OllamaModelFactory} rebuilds clients on each call so Settings changes apply
 * without restart.
 */
@Configuration
public class BankReconAiConfig {

    public static final String CFG_BASE_URL = "ollama_base_url";
    public static final String CFG_CHAT_MODEL = "ollama_chat_model";
    public static final String CFG_EMBED_MODEL = "ollama_embedding_model";
    public static final String CFG_TIMEOUT = "ollama_request_timeout_seconds";

    @Bean
    @Primary
    public OllamaApi ollamaApi(GeneralConfigService generalConfigService) {
        try {
            return buildApi(generalConfigService);
        } catch (Exception e) {
            return OllamaApi.builder().baseUrl("http://localhost:11434").build();
        }
    }

    @Bean
    @Primary
    public OllamaChatModel ollamaChatModel(OllamaApi ollamaApi, GeneralConfigService generalConfigService) {
        String model = "qwen2.5:32b";
        try {
            model = generalConfigService.getConfigValue(CFG_CHAT_MODEL).orElse(model);
        } catch (Exception ignored) {
            // tests / slice contexts may not have general_config available
        }
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(chatOptions(model))
                .build();
    }

    @Bean
    @Primary
    public OllamaEmbeddingModel ollamaEmbeddingModel(OllamaApi ollamaApi, GeneralConfigService generalConfigService) {
        String model = "nomic-embed-text";
        try {
            model = generalConfigService.getConfigValue(CFG_EMBED_MODEL).orElse(model);
        } catch (Exception ignored) {
            // tests / slice contexts may not have general_config available
        }
        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder().model(model).build())
                .build();
    }

    @Bean
    ChatClient bankReconChatClient(OllamaChatModel ollamaChatModel) {
        return ChatClient.builder(ollamaChatModel).build();
    }

    static OllamaOptions chatOptions(String chatModel) {
        return OllamaOptions.builder()
                .model(chatModel)
                .temperature(0.0)
                .numPredict(2048)
                .build();
    }

    static OllamaApi buildApi(GeneralConfigService generalConfigService) {
        String url = generalConfigService.getConfigValue(CFG_BASE_URL).orElse("http://localhost:11434");
        int timeout = parseInt(generalConfigService.getConfigValue(CFG_TIMEOUT).orElse("120"), 120);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.min(timeout, 30)));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));
        MappingJackson2HttpMessageConverter json = new MappingJackson2HttpMessageConverter();
        json.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                MediaType.APPLICATION_OCTET_STREAM,
                MediaType.TEXT_PLAIN));
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> converters.add(0, json))
                .requestInterceptor(new NonStreamingChatInterceptor());
        return OllamaApi.builder().baseUrl(url).restClientBuilder(restClientBuilder).build();
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
    BatchMappingResponse mapBatch(String prompt);
}

interface NarrationEmbedder {
    float[] embed(String text);
}
