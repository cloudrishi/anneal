package com.rish.anneal.llm.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * MicroProfile ConfigMapping for LLM provider settings.
 * Structure matches application.yml anneal.llm hierarchy exactly.
 *
 * <pre>
 * anneal:
 *   llm:
 *     provider: ollama
 *     ollama:
 *       base-url: http://localhost:11434
 *       fix-model: codellama:13b
 *       prose-model: llama3.1:8b
 *     anthropic:
 *       api-key: sk-ant-...
 *       model: claude-sonnet-4-6
 *     allow-cloud-fallback: false
 * </pre>
 */
@ConfigMapping(prefix = "anneal.llm")
public interface LlmConfig {

    /** Active provider — ollama (default) or anthropic */
    @WithDefault("ollama")
    String provider();

    /** Ollama-specific config */
    Ollama ollama();

    /** Anthropic-specific config */
    Anthropic anthropic();

    /** Enable Anthropic cloud fallback for MANUAL effort findings. */
    @WithDefault("false")
    boolean allowCloudFallback();

    /** LLM request timeout in seconds. 120s accommodates codellama:13b cold start. */
    @WithDefault("120")
    int timeoutSeconds();

    /**
     * Master switch for LLM enrichment.
     * When false, findings are returned with template fix suggestions only.
     */
    @WithDefault("true")
    boolean enrichmentEnabled();

    interface Ollama {
        /** Ollama base URL. */
        @WithDefault("http://localhost:11434")
        String baseUrl();

        /**
         * Model for code fix explanations — BREAKING severity findings.
         * codellama:13b gives the best code reasoning on M1 Pro 32GB.
         */
        @WithDefault("codellama:13b")
        String fixModel();

        /**
         * Model for prose explanations — DEPRECATED and MODERNIZATION findings.
         * llama3.1:8b is faster and produces better natural language.
         */
        @WithDefault("llama3.1:8b")
        String proseModel();
    }

    interface Anthropic {
        /** Anthropic API key — required only when allow-cloud-fallback is true. */
        Optional<String> apiKey();

        /** Anthropic model for deep refactors requiring complex reasoning. */
        @WithDefault("claude-sonnet-4-6")
        String model();
    }
}
