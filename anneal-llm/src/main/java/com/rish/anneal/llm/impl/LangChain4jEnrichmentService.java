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
import java.util.concurrent.*;
import java.util.function.Consumer;

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
 * <h2>Async enrichment</h2>
 * {@link #enrichAllAsync} is the preferred scan path. It returns immediately and
 * persists each enrichment result as it completes via
 * {@code ScanResultRepository.updateFindingEnrichment()}. The frontend polls
 * {@code GET /api/scans/{scanId}} and sees explanations fill in progressively.
 *
 * <h2>Dependency boundary</h2>
 * {@code anneal-llm} depends only on {@code anneal-core}. It has no knowledge of
 * {@code anneal-store} or {@code anneal-api}. Persistence is the caller's concern,
 * injected via the {@code Consumer<EnrichedFix>} callback.
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
    private ChatModel cloudModel;

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

    /**
     * Enriches a single finding with an LLM-generated explanation.
     *
     * <p>Selects the appropriate model based on finding severity and effort:
     * <ul>
     *   <li>MANUAL effort + cloud enabled → {@code claude-sonnet-4-6}</li>
     *   <li>BREAKING severity → {@code codellama:13b}</li>
     *   <li>DEPRECATED / MODERNIZATION → {@code llama3.1:8b}</li>
     * </ul>
     *
     * <p>Version facts ({@code introducedIn}, {@code removedIn}, {@code effort}) from
     * the {@code MigrationRule} are injected into the prompt as hard constraints via
     * {@link FixPrompts#userMessage(Finding, MigrationRule)}. This prevents the model
     * from substituting its own (potentially hallucinated) version knowledge.
     *
     * <p>The raw model response is passed through {@link FixPrompts#clean(String)} before
     * returning — strips {@code [INST]}, {@code <<SYS>>}, and BOS/EOS artefacts produced
     * by {@code codellama:13b}'s instruction-tuning format.
     *
     * <p>Failure-isolated — any exception (network, timeout, blank response) is caught,
     * logged at WARN level, and returns {@code Optional.empty()}. The scan response
     * is never affected by a single enrichment failure.
     *
     * @param finding the finding to enrich — provides ruleId, severity, originalCode
     * @param rule    the rule that produced this finding — provides version facts for
     *                prompt anchoring; must not be null
     * @return an {@link EnrichedFix} with explanation and model attribution,
     * or {@code Optional.empty()} on failure, blank response, or when
     * enrichment is disabled via {@code anneal.llm.enrichment-enabled: false}
     */
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

    /**
     * Enriches all findings in parallel using a fixed thread pool bounded by
     * {@code enrichmentConcurrency}. Each finding is submitted as a
     * {@code CompletableFuture} with a per-finding timeout equal to
     * {@code timeoutSeconds}. Failures and timeouts are isolated — one bad
     * call never blocks or fails the others.
     *
     * <p>Uses a fixed thread pool rather than virtual threads because
     * {@code ChatModel.chat()} is a long-blocking call (5–30s) holding a thread
     * for GPU processing time. Virtual threads offer no advantage here — the
     * bottleneck is Ollama throughput, not thread scheduling.
     *
     * <p>Total wall-clock time approaches the slowest batch in the pool rather
     * than the sum of all calls. Mixed-model scans (BREAKING + DEPRECATED findings)
     * get the most benefit as codellama:13b and llama3.1:8b calls overlap.
     */
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
        int concurrency = config.enrichmentConcurrency();

        log.infof("Starting parallel enrichment — %d findings, concurrency: %d",
                findings.size(), concurrency);

        long start = System.currentTimeMillis();

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {

            List<CompletableFuture<Void>> futures = findings.stream()
                    .map(finding -> CompletableFuture
                            .runAsync(() -> {
                                MigrationRule rule = ruleById.get(finding.getRuleId());
                                if (rule == null) {
                                    log.warnf("No MigrationRule for ruleId '%s' — skipping finding %s",
                                            finding.getRuleId(), finding.getFindingId());
                                    return;
                                }
                                enrich(finding, rule).ifPresent(fix ->
                                        results.put(finding.getFindingId(), fix));
                            }, executor)
                            .orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
                            .exceptionally(e -> {
                                log.warnf("Enrichment timed out or failed for finding %s: %s",
                                        finding.getFindingId(), e.getMessage());
                                return null;
                            }))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        long elapsed = System.currentTimeMillis() - start;
        log.infof("Enrichment complete — %d/%d findings enriched in %dms",
                results.size(), findings.size(), elapsed);

        return Collections.unmodifiableMap(results);
    }

    /**
     * Enriches all findings asynchronously, invoking {@code onEnriched} after each
     * successful enrichment. Returns immediately — the scan response is not blocked.
     *
     * <p>The {@code onEnriched} callback is provided by the caller ({@code ScanResource})
     * and is responsible for persistence. This keeps {@code anneal-llm} free of any
     * dependency on {@code anneal-store}.
     *
     * <p>Uses the same fixed thread pool and per-finding timeout as {@link #enrichAll}.
     * The executor is shut down automatically once all futures complete.
     *
     * @param findings   findings to enrich
     * @param ruleById   map of ruleId → MigrationRule for version-fact injection
     * @param onEnriched called after each successful enrichment — caller persists the result
     */
    @Override
    public void enrichAllAsync(
            List<Finding> findings,
            Map<String, MigrationRule> ruleById,
            Consumer<EnrichedFix> onEnriched
    ) {
        if (!config.enrichmentEnabled()) {
            log.debug("LLM enrichment disabled — skipping async enrichment");
            return;
        }

        int concurrency = config.enrichmentConcurrency();

        log.infof("Starting async enrichment — %d findings, concurrency: %d",
                findings.size(), concurrency);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        List<CompletableFuture<Void>> futures = findings.stream()
                .map(finding -> CompletableFuture
                        .runAsync(() -> {
                            MigrationRule rule = ruleById.get(finding.getRuleId());
                            if (rule == null) {
                                log.warnf("No MigrationRule for ruleId '%s' — skipping finding %s",
                                        finding.getRuleId(), finding.getFindingId());
                                return;
                            }
                            enrich(finding, rule).ifPresent(fix -> {
                                onEnriched.accept(fix);
                                log.debugf("Async enriched finding %s via %s",
                                        fix.findingId(), fix.model().modelName());
                            });
                        }, executor)
                        .orTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
                        .exceptionally(e -> {
                            log.warnf("Async enrichment timed out or failed for finding %s: %s",
                                    finding.getFindingId(), e.getMessage());
                            return null;
                        }))
                .toList();

        // Shut down executor once all futures complete — runs entirely in background
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, e) -> {
                    executor.shutdown();
                    log.infof("Async enrichment complete for %d findings", findings.size());
                });
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
