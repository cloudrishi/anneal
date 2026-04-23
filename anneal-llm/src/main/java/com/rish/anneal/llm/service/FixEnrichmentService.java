package com.rish.anneal.llm.service;

import com.rish.anneal.core.model.Finding;
import com.rish.anneal.llm.model.EnrichedFix;

import java.util.List;
import java.util.Map;

/**
 * Enriches findings with LLM-generated fix explanations.
 * Implementations are responsible for model selection and prompt construction.
 */
public interface FixEnrichmentService {

    /**
     * Enriches a single finding with an LLM-generated explanation.
     *
     * @param finding the finding to enrich
     * @return enrichment result — never null; explanation is empty string on failure
     */
    EnrichedFix enrich(Finding finding);

    /**
     * Enriches a list of findings.
     * Returns a map of findingId → EnrichedFix for easy lookup at the call site.
     * Failures are isolated — one bad LLM call does not fail the batch.
     *
     * @param findings findings to enrich
     * @return map of findingId to EnrichedFix, may be smaller than input on partial failure
     */
    Map<String, EnrichedFix> enrichAll(List<Finding> findings);
}
