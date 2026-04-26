package com.rish.anneal.llm.prompt;

import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.rule.MigrationRule;

/**
 * Prompt templates for LLM fix enrichment.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li>Three system messages — one per model role (CODE, PROSE, CLOUD). Different
 *       models respond to different instruction styles; codellama needs terse, explicit
 *       rules, while Anthropic handles nuanced instructions naturally.</li>
 *   <li>Version facts are injected as hard constraints in the user message under a
 *       clearly labelled section. The model is told explicitly that these values come
 *       from static analysis and must not be substituted. This addresses the observed
 *       codellama:13b hallucination of version numbers in prose.</li>
 *   <li>The user message template is shared across all three models — the only
 *       variable is which system message is selected.</li>
 *   <li>{@code clean()} is called on every response before returning — codellama:13b
 *       bleeds [INST]/[/INST] and &lt;&lt;SYS&gt;&gt; tokens from its fine-tuning format
 *       into outputs when the prompt resembles an instruction template.</li>
 * </ul>
 */
public final class FixPrompts {

    private FixPrompts() {}

    // ─── System messages ──────────────────────────────────────────────────────

    /**
     * For codellama:13b — BREAKING severity findings.
     * Terse, rule-based instructions work better than conversational framing
     * with code-specialised models.
     */
    public static final String CODE_SYSTEM = """
            You are a Java migration expert specialising in JVM internals, module system \
            encapsulation, and breaking API changes.
            Your job: explain one migration finding in 2–3 sentences.
            
            RULES (hard constraints — do not violate):
            1. Version facts are provided in the user message under "Version facts". \
               DO NOT substitute Java version numbers from your training data.
            2. DO NOT repeat the detected code or the suggested fix verbatim.
            3. DO NOT add code examples or snippets.
            4. Stop after sentence 3. Output nothing after the third sentence.
            """;

    /**
     * For llama3.1:8b — DEPRECATED and MODERNIZATION findings.
     * These findings need natural language that explains the "why", not JVM internals.
     */
    public static final String PROSE_SYSTEM = """
            You are a Java migration assistant writing explanations for a developer review tool.
            Your job: explain one migration finding in 2–3 sentences.
            Focus on why the change matters and what the replacement achieves.
            
            RULES (hard constraints — do not violate):
            1. Version facts are provided in the user message under "Version facts". \
               DO NOT substitute Java version numbers from your training data.
            2. Write for a developer who already sees the code — do not repeat it.
            3. Stop after sentence 3. Output nothing after the third sentence.
            """;

    /**
     * For claude-sonnet-4-6 — MANUAL effort findings requiring deep reasoning.
     * These have no safe automatic replacement; the explanation must guide a
     * human refactor and surface non-obvious contract implications.
     */
    public static final String CLOUD_SYSTEM = """
            You are a senior Java architect reviewing a migration finding marked MANUAL effort.
            There is no safe automatic replacement — the developer must refactor by hand.
            
            Write exactly 3 sentences structured as:
              Sentence 1 — Why this pattern is problematic in the target Java version.
              Sentence 2 — The recommended migration approach.
              Sentence 3 — Any concurrency, safety, or API-contract considerations the developer must know.
            
            RULES (hard constraints — do not violate):
            1. Version facts are provided under "Version facts". \
               DO NOT substitute version numbers from your training data.
            2. Stop after sentence 3.
            """;

    // ─── User message ─────────────────────────────────────────────────────────

    /**
     * Builds the user message, injecting authoritative version facts from the rule
     * as explicit constraints so the model cannot substitute parametric memory.
     *
     * <p>The "Version facts" section is placed first — most decoder-based models
     * weight earlier tokens more heavily than tokens deep in the context.
     */
    public static String userMessage(Finding finding, MigrationRule rule) {
        String introducedIn = versionLabel(rule.getIntroducedIn());
        String removedIn    = rule.getRemovedIn() != null
                ? versionLabel(rule.getRemovedIn())
                : "not yet removed — deprecated for removal (treat as future breaking change)";

        return """
                ── Version facts (from static analysis — authoritative, do not override) ──
                Rule ID            : %s
                Category           : %s
                Severity           : %s
                Effort required    : %s
                Concern introduced : %s
                Hard break/removal : %s
                
                ── Detected code ──
                %s
                
                ── Suggested replacement ──
                %s
                
                Explain why this change is necessary and what the suggested replacement achieves.
                Use the version facts above. Do not substitute your own version knowledge.
                """.formatted(
                finding.getRuleId(),
                rule.getCategory().name(),
                finding.getSeverity().name(),
                rule.getEffort().name(),
                introducedIn,
                removedIn,
                finding.getOriginalCode().strip(),
                rule.getFixTemplate().getSuggestedCode().strip()
        );
    }

    // ─── Response cleanup ─────────────────────────────────────────────────────

    /**
     * Strips artefacts produced by codellama:13b when its instruction-tuning
     * format leaks into the output. Called on every model response unconditionally.
     *
     * <p>Observed artefacts:
     * <ul>
     *   <li>{@code [INST]} / {@code [/INST]} — instruction markers</li>
     *   <li>{@code <<SYS>>} / {@code <</SYS>>} — system block markers</li>
     *   <li>{@code <s>} / {@code </s>} — BOS/EOS tokens</li>
     * </ul>
     */
    public static String clean(String raw) {
        if (raw == null || raw.isBlank()) return "";

        return raw
                // Instruction markers
                .replaceAll("\\[/?INST]", "")
                // System block markers (non-greedy, don't strip content between them)
                .replaceAll("<</?SYS>>", "")
                // BOS/EOS tokens — strip the tags, keep any content between them
                .replace("<s>", "").replace("</s>", "")
                // Collapse runs of blank lines to a single newline
                .replaceAll("(?m)(^\\s*$\\n){2,}", "\n")
                .strip();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Converts JavaVersion enum (V8, V11, V25…) to "Java 8", "Java 11", etc. */
    private static String versionLabel(Object javaVersion) {
        // JavaVersion enum is in anneal-core; .name() returns "V8", "V11", etc.
        String name = javaVersion.toString();
        return "Java " + name.replace("V", "");
    }
}
