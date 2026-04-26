package com.rish.anneal.llm.provider;

import com.rish.anneal.llm.config.LlmConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import io.quarkus.arc.log.LoggerName;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * Builds and owns the {@link ChatModel} instances for the LLM layer.
 *
 * <h2>Change from previous version</h2>
 * Previously this factory built {@code FixExplainer} AiService proxies. It now
 * exposes raw {@code ChatModel} instances so that {@code LangChain4jEnrichmentService}
 * can construct per-finding system messages via {@code ChatModel.chat(system, user)}.
 * The AiService proxy approach is retained in the codebase for any future streaming
 * or tool-use work, but the enrichment path uses direct model calls.
 *
 * <h2>Cloud model</h2>
 * The Anthropic model is built only when {@code allow-cloud-fallback: true} and
 * {@code ANTHROPIC_API_KEY} is present and non-blank. {@code cloudModel()} returns
 * {@code Optional.empty()} otherwise — callers must check before routing to cloud.
 */
@ApplicationScoped
public class LlmProviderFactory {

    @Inject
    LlmConfig config;
    @LoggerName("anneal.llm")
    Logger log;

    private ChatModel codeModelInstance;
    private ChatModel proseModelInstance;
    private ChatModel cloudModelInstance; // null if not configured

    @PostConstruct
    void init() {
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds());

        codeModelInstance = OllamaChatModel.builder()
                .baseUrl(config.ollama().baseUrl())
                .modelName(config.ollama().fixModel())
                .timeout(timeout)
                .build();

        proseModelInstance = OllamaChatModel.builder()
                .baseUrl(config.ollama().baseUrl())
                .modelName(config.ollama().proseModel())
                .timeout(timeout)
                .build();

        if (config.allowCloudFallback()) {
            String apiKey = config.anthropic().apiKey().orElse("");
            if (apiKey != null && !apiKey.isBlank()) {
                cloudModelInstance = AnthropicChatModel.builder()
                        .apiKey(apiKey)
                        .modelName(config.anthropic().model())
                        .timeout(timeout)
                        .build();
                log.infof("Anthropic cloud model enabled: %s", config.anthropic().model());
            } else {
                log.warn("allow-cloud-fallback: true but ANTHROPIC_API_KEY is absent or blank — cloud model disabled");
            }
        }
    }

    /**
     * Code model — codellama:13b by default. Used for BREAKING severity findings.
     */
    public ChatModel codeModel() {
        return codeModelInstance;
    }

    /**
     * Prose model — llama3.1:8b by default. Used for DEPRECATED and MODERNIZATION findings.
     */
    public ChatModel proseModel() {
        return proseModelInstance;
    }

    /**
     * Cloud model — claude-sonnet-4-6 by default.
     * Returns {@code Optional.empty()} when cloud fallback is disabled or the API key
     * is absent. Callers must gate on this before routing MANUAL findings to cloud.
     */
    public Optional<ChatModel> cloudModel() {
        return Optional.ofNullable(cloudModelInstance);
    }
}
