package com.rish.anneal.llm.prompt;

/**
 * Prompt templates for LLM fix enrichment.
 * Constants only — no logic, no dependencies.
 * Kept here so prompt changes are reviewed as code changes.
 */
public final class FixPrompts {

    private FixPrompts() {}

    /**
     * System prompt for the fix explainer.
     * Tightened to prevent codellama:13b from generating extra [INST] blocks
     * or continuing with additional prompt examples after the explanation.
     */
    public static final String FIX_EXPLAINER_SYSTEM = """
            You are a Java migration expert helping developers migrate codebases to Java 25.
            Your role is to explain why a specific code pattern is problematic and what the suggested fix achieves.

            Rules:
            - Respond in 2-3 sentences maximum.
            - Be specific to the detected code — do not give generic Java advice.
            - Reference the JEP or migration guide only if it adds clarity.
            - Do not repeat the suggested code — explain what it does and why it is correct.
            - If the fix is non-trivial, note what the developer must verify manually.
            - Stop after your explanation. Do not generate additional examples, prompts, or follow-up questions.
            - Do not output any text after your explanation ends.
            """;

    /**
     * User prompt template for fix enrichment.
     * Explicit "End of explanation." instruction prevents the model from
     * continuing with additional [INST] blocks after the response.
     *
     * Placeholders: {ruleId}, {severity}, {originalCode}, {suggestedCode}, {referenceUrl}
     */
    public static final String FIX_EXPLAINER_USER = """
            Rule: {ruleId} ({severity})
            Detected code:
            {originalCode}

            Suggested fix:
            {suggestedCode}

            Reference: {referenceUrl}

            Explain this migration fix to a Java developer in 2-3 sentences. Stop after your explanation.
            """;

    /**
     * Builds the user prompt by substituting finding-specific values.
     */
    public static String buildUserPrompt(
            String ruleId,
            String severity,
            String originalCode,
            String suggestedCode,
            String referenceUrl) {

        return FIX_EXPLAINER_USER
                .replace("{ruleId}", ruleId)
                .replace("{severity}", severity)
                .replace("{originalCode}", originalCode != null ? originalCode : "N/A")
                .replace("{suggestedCode}", suggestedCode != null ? suggestedCode : "No template available")
                .replace("{referenceUrl}", referenceUrl != null ? referenceUrl : "N/A");
    }

    /**
     * Cleans LLM output artifacts from codellama:13b responses.
     * Strips [INST:...] blocks and anything after them — these are prompt
     * continuation artifacts where the model generates its own next prompt.
     *
     * @param raw raw LLM response
     * @return cleaned explanation, trimmed
     */
    public static String clean(String raw) {
        if (raw == null) return "";

        // Strip [INST...] blocks and everything after — codellama hallucination artifact
        int instIdx = raw.indexOf("[INST");
        if (instIdx > 0) {
            raw = raw.substring(0, instIdx);
        }

        // Strip leading/trailing whitespace and newlines
        return raw.strip();
    }
}
