package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.general.GeneralConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
class SpringAiStructuredLlmClient implements StructuredLlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiStructuredLlmClient.class);
    private final GeneralConfigService generalConfigService;
    private final boolean traceEnabled;
    private final AtomicInteger invocationCount = new AtomicInteger();

    SpringAiStructuredLlmClient(
            GeneralConfigService generalConfigService,
            @Value("${bankrecon.llm.trace-enabled:true}") boolean traceEnabled) {
        this.generalConfigService = generalConfigService;
        this.traceEnabled = traceEnabled;
    }

    @Override
    public LlmBatchResult mapBatch(String prompt, int maxTokens) {
        invocationCount.incrementAndGet();
        String callId = LlmCallContext.nextCallId();
        String apiKey = apiKey();
        Endpoint endpoint = chatEndpoint();
        String catalog = MappingPrompt.catalogSection(prompt);
        String catalogHash = catalog.isEmpty() ? "none" : LlmTrace.shortSha256(catalog);
        int estimated = MappingPrompt.estimateTokens(prompt);
        LlmTrace.info(log, apiKey, LlmTrace.beforeLine(
                callId,
                "chat",
                endpoint.provider(),
                endpoint.baseUrl(),
                endpoint.model(),
                LlmTrace.transactionCount(prompt),
                LlmTrace.catalogLedgerCount(catalog),
                catalogHash,
                estimated,
                Integer.toString(maxTokens),
                Boolean.toString(BankReconAiConfig.THINKING_DISABLED),
                apiKey));
        if (estimated > MappingPrompt.SAFE_PROMPT_TOKENS) {
            LlmTrace.warn(log, apiKey,
                    "callId=" + callId + " LLM mapping prompt exceeds the safe limit of "
                            + MappingPrompt.SAFE_PROMPT_TOKENS + " tokens (estimated " + estimated + ")");
        }
        if (LlmTrace.tracing(traceEnabled)) {
            String request = LlmTrace.requestText(
                    prompt,
                    endpoint.provider(),
                    endpoint.baseUrl(),
                    endpoint.model(),
                    maxTokens,
                    BankReconAiConfig.THINKING_DISABLED,
                    LlmCallContext.catalogMemory(),
                    apiKey);
            LlmTrace.trace(true, apiKey, LlmTrace.prefixLines(callId, request));
        }
        boolean ollama = BankReconAiConfig.ollamaProvider(generalConfigService);
        ChatModel chatModel = BankReconAiConfig.buildChatModel(generalConfigService);
        LlmHttpCapture.clear();
        long started = System.nanoTime();
        try {
            var request = ChatClient.builder(chatModel).build().prompt().user(prompt);
            ChatResponse response = (ollama
                    ? request.options(OllamaChatOptions.builder()
                            .model(endpoint.model())
                            .temperature(0.0)
                            .numPredict(maxTokens))
                    : request.options(OpenAiChatOptions.builder()
                            .model(endpoint.model())
                            .temperature(0.0)
                            .maxTokens(maxTokens)))
                    .call()
                    .chatResponse();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LlmHttpCapture.Snapshot http = LlmHttpCapture.take();
            if (http != null && http.status() >= 400) {
                logFailure(callId, apiKey, elapsedMs, http.status(), http.body(), null);
                return LlmBatchResult.failed();
            }
            Usage usage = response == null || response.getMetadata() == null
                    ? null
                    : response.getMetadata().getUsage();
            Integer promptTokens = usage == null ? null : usage.getPromptTokens();
            Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
            String finishReason = response == null || response.getResult() == null
                    || response.getResult().getMetadata() == null
                    ? null
                    : response.getResult().getMetadata().getFinishReason();
            LlmTrace.info(log, apiKey, LlmTrace.afterLine(
                    callId,
                    elapsedMs,
                    http == null ? null : http.status(),
                    finishReason,
                    promptTokens,
                    completionTokens));
            String text = response == null || response.getResult() == null || response.getResult().getOutput() == null
                    ? ""
                    : response.getResult().getOutput().getText();
            String traced = text == null || text.isBlank()
                    ? (http == null ? "" : http.body())
                    : text;
            if (LlmTrace.tracing(traceEnabled)) {
                LlmTrace.trace(true, apiKey, LlmTrace.prefixLines(callId, LlmTrace.responseText(traced)));
            }
            MappingResponseParser.Parsed parsed = MappingResponseParser.parse(text);
            boolean truncated = "length".equalsIgnoreCase(finishReason) || parsed.incomplete();
            return new LlmBatchResult(
                    parsed.mappings(),
                    truncated,
                    text == null ? "" : text,
                    parsed.returned(),
                    parsed.mappings().size(),
                    true);
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LlmHttpCapture.Snapshot http = LlmHttpCapture.take();
            Integer status = http == null || http.status() <= 0 ? null : http.status();
            String body = http == null ? "" : http.body();
            if (e instanceof RestClientResponseException responseError) {
                status = responseError.getStatusCode().value();
                if (body == null || body.isEmpty()) {
                    body = responseError.getResponseBodyAsString();
                }
            }
            logFailure(callId, apiKey, elapsedMs, status, body, e);
            return LlmBatchResult.failed();
        } finally {
            LlmHttpCapture.clear();
        }
    }

    int invocationCount() {
        return invocationCount.get();
    }

    private void logFailure(String callId, String apiKey, long elapsedMs, Integer status, String body, Exception error) {
        LlmTrace.error(log, apiKey, callId,
                "elapsedMs=" + elapsedMs
                        + " httpStatus=" + (status == null ? "none" : status)
                        + " errorBody=" + LlmTrace.errorBody(body),
                error);
    }

    private String apiKey() {
        return generalConfigService.getConfigValue(BankReconAiConfig.CFG_API_KEY).orElse("");
    }

    private Endpoint chatEndpoint() {
        if (BankReconAiConfig.ollamaProvider(generalConfigService)) {
            return new Endpoint(
                    BankReconAiConfig.PROVIDER_OLLAMA,
                    generalConfigService.getConfigValue(BankReconAiConfig.CFG_BASE_URL)
                            .orElse(BankReconAiConfig.DEFAULT_OLLAMA_URL),
                    generalConfigService.getConfigValue(BankReconAiConfig.CFG_CHAT_MODEL)
                            .orElse(BankReconAiConfig.DEFAULT_OLLAMA_CHAT_MODEL));
        }
        return new Endpoint(
                BankReconAiConfig.PROVIDER_OMLX,
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_BASE_URL)
                        .orElse(BankReconAiConfig.DEFAULT_CHAT_URL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_CHAT_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_CHAT_MODEL));
    }

    private record Endpoint(String provider, String baseUrl, String model) {}
}

@Service
class SpringAiNarrationEmbedder implements NarrationEmbedder {

    private static final Logger log = LoggerFactory.getLogger(SpringAiNarrationEmbedder.class);
    private final GeneralConfigService generalConfigService;
    private final boolean traceEnabled;

    SpringAiNarrationEmbedder(
            GeneralConfigService generalConfigService,
            @Value("${bankrecon.llm.trace-enabled:true}") boolean traceEnabled) {
        this.generalConfigService = generalConfigService;
        this.traceEnabled = traceEnabled;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String callId = LlmCallContext.nextCallId();
        String apiKey = generalConfigService.getConfigValue(BankReconAiConfig.CFG_API_KEY).orElse("");
        Endpoint endpoint = embedEndpoint();
        LlmTrace.info(log, apiKey, LlmTrace.beforeLine(
                callId,
                "embedding",
                endpoint.provider(),
                endpoint.baseUrl(),
                endpoint.model(),
                0,
                0,
                "none",
                MappingPrompt.estimateTokens(text),
                "none",
                "none",
                apiKey));
        if (LlmTrace.tracing(traceEnabled)) {
            LlmTrace.trace(true, apiKey, LlmTrace.prefixLines(callId,
                    LlmTrace.embeddingRequestText(
                            endpoint.provider(), endpoint.baseUrl(), endpoint.model(), text, apiKey)));
        }
        LlmHttpCapture.clear();
        long started = System.nanoTime();
        try {
            float[] vector = BankReconAiConfig.buildEmbeddingModel(generalConfigService).embed(text);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LlmHttpCapture.Snapshot http = LlmHttpCapture.take();
            if (http != null && http.status() >= 400) {
                LlmTrace.error(log, apiKey, callId,
                        "elapsedMs=" + elapsedMs
                                + " httpStatus=" + http.status()
                                + " errorBody=" + LlmTrace.errorBody(http.body()),
                        null);
                return new float[0];
            }
            LlmTrace.info(log, apiKey, LlmTrace.afterLine(
                    callId, elapsedMs, http == null ? null : http.status(), null, null, null));
            if (LlmTrace.tracing(traceEnabled)) {
                String body = http == null ? "" : http.body();
                LlmTrace.trace(true, apiKey, LlmTrace.prefixLines(callId, LlmTrace.responseText(body)));
            }
            return vector == null ? new float[0] : vector;
        } catch (Exception e) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            LlmHttpCapture.Snapshot http = LlmHttpCapture.take();
            Integer status = http == null || http.status() <= 0 ? null : http.status();
            String body = http == null ? "" : http.body();
            if (e instanceof RestClientResponseException responseError) {
                status = responseError.getStatusCode().value();
                if (body == null || body.isEmpty()) {
                    body = responseError.getResponseBodyAsString();
                }
            }
            LlmTrace.error(log, apiKey, callId,
                    "elapsedMs=" + elapsedMs
                            + " httpStatus=" + (status == null ? "none" : status)
                            + " errorBody=" + LlmTrace.errorBody(body),
                    e);
            return new float[0];
        } finally {
            LlmHttpCapture.clear();
        }
    }

    private Endpoint embedEndpoint() {
        if (BankReconAiConfig.ollamaProvider(generalConfigService)) {
            return new Endpoint(
                    BankReconAiConfig.PROVIDER_OLLAMA,
                    generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_URL)
                            .orElse(BankReconAiConfig.DEFAULT_OLLAMA_URL),
                    generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_MODEL)
                            .orElse(BankReconAiConfig.DEFAULT_OLLAMA_EMBED_MODEL));
        }
        return new Endpoint(
                BankReconAiConfig.PROVIDER_OMLX,
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_BASE_URL)
                        .orElse(BankReconAiConfig.DEFAULT_CHAT_URL),
                generalConfigService.getConfigValue(BankReconAiConfig.CFG_MLX_EMBED_MODEL)
                        .orElse(BankReconAiConfig.DEFAULT_OMLX_EMBED_MODEL));
    }

    private record Endpoint(String provider, String baseUrl, String model) {}
}
