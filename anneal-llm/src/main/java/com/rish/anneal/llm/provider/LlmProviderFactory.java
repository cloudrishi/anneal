package com.rish.anneal.llm.provider;

import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.Severity;
import com.rish.anneal.llm.config.LlmConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;

/**
 * Builds and owns the three ChatModel instances.
 * Routes each Finding to the appropriate model based on severity and effort.
 *
 * Routing logic:
 * - MANUAL effort + cloud-fallback enabled → Anthropic (deep refactors)
 * - BREAKING severity                      → code model (codellama:13b)
 * - DEPRECATED / MODERNIZATION             → prose model (llama3.1:8b)
 */
@ApplicationScoped
public class LlmProviderFactory {

    private static final Logger log = Logger.getLogger(LlmProviderFactory.class);

    @Inject
    LlmConfig config;

    private ChatModel codeModel;
    private ChatModel proseModel;
    private ChatModel cloudModel;  // null when cloud-fallback is false or key absent

    @PostConstruct
    void init() {
        Duration timeout = Duration.ofSeconds(config.timeoutSeconds());

        codeModel = OllamaChatModel.builder()
                .baseUrl(config.ollama().baseUrl())
                .modelName(config.ollama().fixModel())
                .timeout(timeout)
                .build();

        proseModel = OllamaChatModel.builder()
                .baseUrl(config.ollama().baseUrl())
                .modelName(config.ollama().proseModel())
                .timeout(timeout)
                .build();

        if (config.allowCloudFallback()) {
            config.anthropic().apiKey().ifPresentOrElse(
                    key -> {
                        cloudModel = AnthropicChatModel.builder()
                                .apiKey(key)
                                .modelName(config.anthropic().model())
                                .timeout(timeout)
                                .build();
                        log.infof("Cloud fallback enabled — using %s", config.anthropic().model());
                    },
                    () -> log.warn("anneal.llm.allow-cloud-fallback=true but no API key configured — cloud fallback disabled")
            );
        }

        log.infof("LLM providers initialised — code: %s, prose: %s, cloud: %s",
                config.ollama().fixModel(),
                config.ollama().proseModel(),
                cloudModel != null ? config.anthropic().model() : "disabled");
    }

    /**
     * Selects the appropriate model for a given finding.
     *
     * @param finding the finding to enrich
     * @return ChatModel to use — never null
     */
    public ChatModel forFinding(Finding finding) {
        if (finding.getEffort() == Effort.MANUAL && cloudModel != null) {
            return cloudModel;
        }
        if (finding.getSeverity() == Severity.BREAKING) {
            return codeModel;
        }
        return proseModel;
    }

    /**
     * Returns the model name string for a given finding — used in EnrichedFix metadata.
     */
    public String modelNameFor(Finding finding) {
        if (finding.getEffort() == Effort.MANUAL && cloudModel != null) {
            return config.anthropic().model();
        }
        if (finding.getSeverity() == Severity.BREAKING) {
            return config.ollama().fixModel();
        }
        return config.ollama().proseModel();
    }
}
