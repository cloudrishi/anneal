package com.rish.anneal.llm.model;

/**
 * Typed representation of the LLM provider and model used to enrich a finding.
 *
 * <p>Sealed — the only permitted implementations are {@link Ollama} and {@link Anthropic}.
 * This allows exhaustive pattern matching in {@code ScanMapper} without a {@code default}
 * branch, and guarantees a compile error if a new provider is added without handling it:
 *
 * <pre>{@code
 * LlmProvider provider = switch (fix.model()) {
 *     case LlmModel.Anthropic a -> LlmProvider.ANTHROPIC;
 *     case LlmModel.Ollama o    -> LlmProvider.OLLAMA;
 * };
 * }</pre>
 *
 * <p>The model name is config-driven and intentionally typed as {@code String} — it
 * reflects whatever the operator has configured (e.g. {@code codellama:13b},
 * {@code llama3.1:8b}, {@code claude-sonnet-4-6}) and cannot be enumerated at
 * compile time.
 *
 * <p>{@code LlmModel} is runtime-only — it is carried on {@link com.rish.anneal.llm.model.EnrichedFix}
 * and flattened into {@code FindingDto.llmProvider} (enum) and {@code FindingDto.llmModel}
 * (String) before crossing the API boundary. It is never persisted.
 */
public sealed interface LlmModel permits LlmModel.Ollama, LlmModel.Anthropic {

    /**
     * The raw model name as configured — passed through to the API response as
     * {@code FindingDto.llmModel} for UI attribution (e.g. "via codellama:13b").
     */
    String modelName();

    /**
     * A locally-hosted model served via Ollama.
     *
     * <p>Used for:
     * <ul>
     *   <li>BREAKING severity findings → {@code codellama:13b} (code reasoning)</li>
     *   <li>DEPRECATED / MODERNIZATION findings → {@code llama3.1:8b} (prose)</li>
     * </ul>
     *
     * <p>No data leaves the machine when this model is selected.
     *
     * @param modelName the Ollama model tag e.g. {@code codellama:13b}, {@code llama3.1:8b}
     */
    record Ollama(String modelName) implements LlmModel {
    }

    /**
     * A cloud-hosted model served via the Anthropic API.
     *
     * <p>Used for MANUAL effort findings that require deep reasoning and cannot be
     * safely auto-replaced. Only active when {@code allow-cloud-fallback: true} and
     * {@code ANTHROPIC_API_KEY} is present.
     *
     * <p>Source code snippets are sent to Anthropic when this model is selected.
     * The operator must explicitly opt in via configuration.
     *
     * @param modelName the Anthropic model identifier e.g. {@code claude-sonnet-4-6}
     */
    record Anthropic(String modelName) implements LlmModel {
    }
}