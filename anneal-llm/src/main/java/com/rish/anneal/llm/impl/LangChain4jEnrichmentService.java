package com.rish.anneal.llm.impl;

import com.rish.anneal.core.model.Effort;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.Severity;
import com.rish.anneal.core.rule.MigrationRule;
import com.rish.anneal.llm.config.LlmConfig;
import com.rish.anneal.llm.model.EnrichedFix;
import com.rish.anneal.llm.model.LlmModel;
import com.rish.anneal.llm.prompt.FixPrompts;
import com.rish.anneal.llm.provider.LlmProviderFactory;
import com.rish.anneal.llm.service.FixEnrichmentService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.arc.log.LoggerName;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j-backed implementation of {@link FixEnrichmentService}.
 *
 * <h2>Model routing</h2>
 * <pre>
 *   MANUAL effort  + cloud enabled  → claude-sonnet-4-6  (deep reasoning, complex refactors)
 *   BREAKING severity               → codellama:13b       (JVM/API internals knowledge)
 *   DEPRECATED / MODERNIZATION      → llama3.1:8b         (natural language prose)
 * </pre>
 *
 * <h2>Why direct ChatModel.chat() instead of AiServices</h2>
 * The previous implementation used {@code AiServices.builder()} to create
 * {@code FixExplainer} proxies. This works well for static prompt templates but
 * becomes awkward when the system message must change per finding (different model
 * roles require different system instructions, and the version-fact constraints
 * are finding-specific). Calling {@code chatModel.chat(systemMsg, userMsg)} directly
 * is more explicit, easier to test, and removes the AiService proxy indirection
 * for what is essentially a single-turn call.
 *
 * <h2>Failure isolation</h2>
 * Every enrichment call is wrapped in try/catch. A failure for one finding is
 * logged at WARN level and returns {@code Optional.empty()} — it never propagates
 * to the scan response or prevents other findings from being enriched.
 */
@ApplicationScoped
public class LangChain4jEnrichmentService implements FixEnrichmentService {

    @Inject
    LlmProviderFactory factory;
    @Inject
    LlmConfig config;
    @LoggerName("anneal.llm")
    Logger log;

    private ChatModel codeModel;
    private ChatModel proseModel;
    private ChatModel cloudModel; // null when allow-cloud-fallback: false or key absent

    @PostConstruct
    void init() {
        this.codeModel = factory.codeModel();
        this.proseModel = factory.proseModel();
        this.cloudModel = factory.cloudModel().orElse(null);

        log.infof("LLM enrichment initialised — code: %s, prose: %s, cloud: %s",
                config.ollama().fixModel(),
                config.ollama().proseModel(),
                cloudModel != null ? config.anthropic().model() : "disabled");
    }

    // ─── FixEnrichmentService ─────────────────────────────────────────────────

    @Override
    public Optional<EnrichedFix> enrich(Finding finding, MigrationRule rule) {
        if (!config.enrichmentEnabled()) return Optional.empty();

        try {
            SelectedModel selected = selectModel(finding, rule);
            String systemPrompt = systemPromptFor(selected.role());
            String userMsg = FixPrompts.userMessage(finding, rule);
            String raw = callModel(selected.model(), systemPrompt, userMsg);
            String explanation = FixPrompts.clean(raw);
            
            if (explanation.isBlank()) {
                log.warnf("Blank explanation returned for finding %s (model: %s)",
                        finding.getFindingId(), selected.llmModel());
                return Optional.empty();
            }

            log.debugf("Enriched finding %s via %s (%d chars)",
                    finding.getFindingId(), selected.llmModel(), explanation.length());

            return Optional.of(new EnrichedFix(finding.getFindingId(), explanation, selected.llmModel()));

        } catch (Exception e) {
            log.warnf("LLM enrichment failed for finding %s [rule=%s]: %s",
                    finding.getFindingId(), finding.getRuleId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Map<String, EnrichedFix> enrichAll(
            List<Finding> findings,
            Map<String, MigrationRule> ruleById
    ) {
        if (!config.enrichmentEnabled()) {
            log.debug("LLM enrichment disabled — returning empty map");
            return Map.of();
        }

        Map<String, EnrichedFix> results = new ConcurrentHashMap<>();

        for (Finding finding : findings) {
            MigrationRule rule = ruleById.get(finding.getRuleId());
            if (rule == null) {
                log.warnf("No MigrationRule found for ruleId '%s' — skipping enrichment for finding %s",
                        finding.getRuleId(), finding.getFindingId());
                continue;
            }
            enrich(finding, rule).ifPresent(fix -> results.put(finding.getFindingId(), fix));
        }

        log.infof("Enrichment complete — %d/%d findings enriched", results.size(), findings.size());

        return Collections.unmodifiableMap(results);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Selects the model and system prompt for a given finding.
     *
     * <p>Cloud fallback is restricted to MANUAL effort — these are the findings
     * that genuinely require deep reasoning. Routing all BREAKING findings to cloud
     * would be expensive and unnecessary; codellama handles code-level explanations
     * well once version facts are pinned in the prompt.
     */
    private SelectedModel selectModel(Finding finding, MigrationRule rule) {
        if (rule.getEffort() == Effort.MANUAL && cloudModel != null) {
            return new SelectedModel(cloudModel, new LlmModel.Anthropic(config.anthropic().model()), ModelRole.CLOUD);
        }
        if (finding.getSeverity() == Severity.BREAKING) {
            return new SelectedModel(codeModel, new LlmModel.Ollama(config.ollama().fixModel()), ModelRole.CODE);
        }
        return new SelectedModel(proseModel, new LlmModel.Ollama(config.ollama().proseModel()), ModelRole.PROSE);
    }

    private static String systemPromptFor(ModelRole role) {
        return switch (role) {
            case CODE -> FixPrompts.CODE_SYSTEM;
            case PROSE -> FixPrompts.PROSE_SYSTEM;
            case CLOUD -> FixPrompts.CLOUD_SYSTEM;
        };
    }

    /**
     * Executes a single-turn chat call with a system + user message.
     *
     * <p>Uses {@code ChatModel.chat(ChatMessage...)} directly rather than an
     * AiService proxy — the per-finding system message variation makes the proxy
     * approach awkward (systemMessageProvider is session-scoped, not call-scoped).
     */
    private String callModel(ChatModel model, String systemPrompt, String userMsg) {
        return model.chat(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userMsg)
        ).aiMessage().text();
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    private enum ModelRole {CODE, PROSE, CLOUD}

    private record SelectedModel(ChatModel model, LlmModel llmModel, ModelRole role) {
    }
}
