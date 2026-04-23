package com.rish.anneal.llm.service;

import com.rish.anneal.core.model.Finding;

/**
 * Computes embedding vectors for findings.
 * The vector is used for similarity search — surfacing related findings
 * and previously resolved patterns in future scans.
 *
 * Returns float[] — caller owns storage. anneal-store persists via pgvector.
 */
public interface EmbeddingService {

    /**
     * Computes a 384-dimensional embedding vector for a finding.
     * Input text: ruleId + severity + originalCode — enough signal for similarity.
     *
     * @param finding the finding to embed
     * @return float[384] — AllMiniLmL6V2 output dimension
     */
    float[] embed(Finding finding);

    /**
     * Builds the text representation used for embedding.
     * Exposed so callers can log or inspect what was embedded.
     */
    String embeddingText(Finding finding);
}
