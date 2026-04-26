package com.rish.anneal.llm.service;

import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.rule.MigrationRule;
import com.rish.anneal.llm.model.EnrichedFix;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contract for LLM-based fix enrichment.
 *
 * <p>The {@code MigrationRule} is required alongside each {@code Finding} so that
 * deterministic facts ({@code introducedIn}, {@code removedIn}, {@code effort}) can
 * be injected into the prompt as hard constraints. This prevents the model from
 * substituting its own (potentially hallucinated) version knowledge.
 *
 * <p>Implementations must be failure-isolated: a bad LLM call for one finding must
 * never prevent enrichment of the others, and must never propagate to the scan response.
 */
public interface FixEnrichmentService {

    /**
     * Enrich a single finding. Returns {@code Optional.empty()} on LLM failure,
     * blank response, or when enrichment is disabled.
     */
    Optional<EnrichedFix> enrich(Finding finding, MigrationRule rule);

    /**
     * Enrich all findings in a scan. Skips any finding whose ruleId is absent from
     * {@code ruleById} (with a warning log) rather than throwing.
     *
     * @param findings  findings produced by the rule engine for this scan
     * @param ruleById  map of ruleId → MigrationRule for all active rules in this scan
     * @return          map of findingId → EnrichedFix for successful enrichments only
     */
    Map<String, EnrichedFix> enrichAll(List<Finding> findings, Map<String, MigrationRule> ruleById);
}
