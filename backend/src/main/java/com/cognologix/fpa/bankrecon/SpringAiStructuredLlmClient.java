package com.cognologix.fpa.bankrecon;

import com.cognologix.fpa.bankrecon.dto.BankReconDtos.BatchMappingResponse;
import com.cognologix.fpa.general.GeneralConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
class SpringAiStructuredLlmClient implements StructuredLlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiStructuredLlmClient.class);
    private final GeneralConfigService generalConfigService;
    private final AtomicInteger invocationCount = new AtomicInteger();

    SpringAiStructuredLlmClient(GeneralConfigService generalConfigService) {
        this.generalConfigService = generalConfigService;
    }

    @Override
    public BatchMappingResponse mapBatch(String prompt) {
        invocationCount.incrementAndGet();
        String model = generalConfigService.getConfigValue(BankReconAiConfig.CFG_CHAT_MODEL)
                .orElse("qwen2.5:32b");
        OllamaApi api = BankReconAiConfig.buildApi(generalConfigService);
        OllamaOptions options = BankReconAiConfig.chatOptions(model);
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(api)
                .defaultOptions(options)
                .build();
        try {
            BatchMappingResponse response = ChatClient.builder(chatModel).build()
                    .prompt()
                    .options(options)
                    .user(prompt)
                    .call()
                    .entity(BatchMappingResponse.class);
            return response != null && response.mappings() != null
                    ? response
                    : new BatchMappingResponse(List.of());
        } catch (Exception e) {
            log.warn("Ollama batch mapping failed: {}", e.getMessage());
            return new BatchMappingResponse(List.of());
        }
    }

    int invocationCount() {
        return invocationCount.get();
    }
}

@Service
class SpringAiNarrationEmbedder implements NarrationEmbedder {

    private static final Logger log = LoggerFactory.getLogger(SpringAiNarrationEmbedder.class);
    private final GeneralConfigService generalConfigService;

    SpringAiNarrationEmbedder(GeneralConfigService generalConfigService) {
        this.generalConfigService = generalConfigService;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }
        String model = generalConfigService.getConfigValue(BankReconAiConfig.CFG_EMBED_MODEL)
                .orElse("nomic-embed-text");
        OllamaApi api = BankReconAiConfig.buildApi(generalConfigService);
        OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
                .ollamaApi(api)
                .defaultOptions(OllamaOptions.builder().model(model).build())
                .build();
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            log.warn("Embedding generation failed: {}", e.getMessage());
            return new float[0];
        }
    }
}
