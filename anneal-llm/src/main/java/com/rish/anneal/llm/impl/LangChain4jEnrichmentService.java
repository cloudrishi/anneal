package com.rish.anneal.llm.impl;

import com.rish.anneal.core.model.Finding;
import com.rish.anneal.llm.config.LlmConfig;
import com.rish.anneal.llm.model.EnrichedFix;
import com.rish.anneal.llm.prompt.FixPrompts;
import com.rish.anneal.llm.provider.LlmProviderFactory;
import com.rish.anneal.llm.prompt.FixPrompts;
import com.rish.anneal.llm.service.FixEnrichmentService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LangChain4j implementation of FixEnrichmentService.
 *
 * Uses AiServices.builder() — programmatic, no @RegisterAiService CDI requirement.
 * Two explainer instances are built at startup: one per Ollama model.
 * Cloud explainer is built on demand if cloud-fallback is enabled.
 *
 * Routing is delegated to LlmProviderFactory.forFinding().
 */
@ApplicationScoped
public class LangChain4jEnrichmentService implements FixEnrichmentService {

    private static final Logger log = Logger.getLogger(LangChain4jEnrichmentService.class);

    /**
     * AiService interface for fix explanation.
     * LangChain4j generates the proxy — prompt templates are declared here.
     */
    interface FixExplainer {
        @SystemMessage(FixPrompts.FIX_EXPLAINER_SYSTEM)
        @UserMessage("""
                Rule: {{ruleId}} ({{severity}})
                Detected code:
                {{originalCode}}

                Suggested fix:
                {{suggestedCode}}

                Reference: {{referenceUrl}}

                Explain this migration fix to a Java developer.
                """)
        String explain(
                @V("ruleId") String ruleId,
                @V("severity") String severity,
                @V("originalCode") String originalCode,
                @V("suggestedCode") String suggestedCode,
                @V("referenceUrl") String referenceUrl
        );
    }

    @Inject LlmProviderFactory factory;
    @Inject LlmConfig config;

    // Explainer instances — built once per model at startup
    private FixExplainer codeExplainer;
    private FixExplainer proseExplainer;
    private FixExplainer cloudExplainer;

    @PostConstruct
    void init() {
        // Explainers built eagerly for code and prose models.
        // Cloud explainer built lazily on first MANUAL finding.
        codeExplainer  = buildExplainer(factory.forFinding(breakingProbe()));
        proseExplainer = buildExplainer(factory.forFinding(modernizationProbe()));
        log.info("FixEnrichmentService initialised");
    }

    private Finding breakingProbe() {
        return Finding.builder()
                .findingId("__probe__").ruleId("__probe__").ruleName("__probe__")
                .severity(com.rish.anneal.core.model.Severity.BREAKING)
                .effort(com.rish.anneal.core.model.Effort.LOW)
                .category(com.rish.anneal.core.model.RuleCategory.JPMS)
                .filePath("").originalCode("").description("")
                .confidence(1.0f).affectsVersion(com.rish.anneal.core.model.JavaVersion.V11)
                .build();
    }

    private Finding modernizationProbe() {
        return Finding.builder()
                .findingId("__probe__").ruleId("__probe__").ruleName("__probe__")
                .severity(com.rish.anneal.core.model.Severity.MODERNIZATION)
                .effort(com.rish.anneal.core.model.Effort.LOW)
                .category(com.rish.anneal.core.model.RuleCategory.LANGUAGE)
                .filePath("").originalCode("").description("")
                .confidence(1.0f).affectsVersion(com.rish.anneal.core.model.JavaVersion.V25)
                .build();
    }

    @Override
    public EnrichedFix enrich(Finding finding) {
        if (!config.enrichmentEnabled()) {
            return new EnrichedFix(finding.getFindingId(), "", "disabled", 0L);
        }

        FixExplainer explainer = selectExplainer(finding);
        String suggestedCode = finding.getFixSuggestion() != null
                ? finding.getFixSuggestion().getSuggestedCode()
                : null;
        String referenceUrl = finding.getReferenceUrl();

        long start = System.currentTimeMillis();
        try {
            String explanation = FixPrompts.clean(explainer.explain(
                    finding.getRuleId(),
                    finding.getSeverity().name(),
                    finding.getOriginalCode(),
                    suggestedCode != null ? suggestedCode : "No template available",
                    referenceUrl != null ? referenceUrl : "N/A"
            ));
            long latency = System.currentTimeMillis() - start;
            log.debugf("Enriched %s in %dms via %s", finding.getFindingId(), latency, factory.modelNameFor(finding));
            return new EnrichedFix(finding.getFindingId(), explanation, factory.modelNameFor(finding), latency);

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warnf("LLM enrichment failed for %s after %dms: %s", finding.getFindingId(), latency, e.getMessage());
            return new EnrichedFix(finding.getFindingId(), "", factory.modelNameFor(finding), latency);
        }
    }

    @Override
    public Map<String, EnrichedFix> enrichAll(List<Finding> findings) {
        Map<String, EnrichedFix> results = new HashMap<>();
        for (Finding finding : findings) {
            EnrichedFix enriched = enrich(finding);
            if (!enriched.explanation().isBlank()) {
                results.put(enriched.findingId(), enriched);
            }
        }
        return results;
    }

    // --- Private helpers ---

    /**
     * Mirrors LlmProviderFactory.forFinding() routing — returns the correct explainer
     * without comparing model object identities.
     */
    private FixExplainer selectExplainer(Finding finding) {
        if (finding.getEffort() == com.rish.anneal.core.model.Effort.MANUAL
                && config.allowCloudFallback()
                && config.anthropic().apiKey().isPresent()) {
            if (cloudExplainer == null) {
                cloudExplainer = buildExplainer(factory.forFinding(finding));
            }
            return cloudExplainer;
        }
        if (finding.getSeverity() == com.rish.anneal.core.model.Severity.BREAKING) {
            return codeExplainer;
        }
        return proseExplainer;
    }

    private FixExplainer buildExplainer(ChatModel model) {
        return AiServices.builder(FixExplainer.class)
                .chatModel(model)
                .build();
    }
}
