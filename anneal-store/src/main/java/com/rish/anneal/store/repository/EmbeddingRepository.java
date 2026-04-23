package com.rish.anneal.store.repository;

import com.rish.anneal.store.entity.FindingEmbeddingEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Persists and queries finding embeddings.
 * Similarity search uses pgvector's cosine distance operator (<=>) via native SQL.
 */
@ApplicationScoped
public class EmbeddingRepository {

    /**
     * Persists an embedding for a finding.
     * Idempotent — replaces existing embedding for the same findingId.
     */
    @Transactional
    public void save(String findingId, String scanId, String ruleId,
                     float[] embedding, String embeddedText) {
        // Delete existing if re-scanning the same finding
        FindingEmbeddingEntity.delete("findingId", findingId);

        FindingEmbeddingEntity entity = new FindingEmbeddingEntity();
        entity.findingId = findingId;
        entity.scanId = scanId;
        entity.ruleId = ruleId;
        entity.embedding = embedding;
        entity.embeddedText = embeddedText;
        entity.persist();
    }

    /**
     * Finds the N most similar findings to a query vector using cosine distance.
     * Excludes the current scan — intended for surfacing patterns from past scans.
     *
     * @param queryVector   384-dim embedding of the query finding
     * @param excludeScanId scan to exclude from results (typically the current scan)
     * @param limit         max results to return
     * @return list of similar CodeEmbeddingEntity, ordered by cosine similarity ascending
     */
    @SuppressWarnings("unchecked")
    public List<FindingEmbeddingEntity> findSimilar(float[] queryVector, String excludeScanId, int limit) {
        String vectorLiteral = toVectorLiteral(queryVector);
        return FindingEmbeddingEntity
                .getEntityManager()
                .createNativeQuery("""
                        SELECT * FROM anneal.finding_embeddings
                        WHERE scan_id != :excludeScanId
                        ORDER BY embedding <=> CAST(:vector AS vector)
                        LIMIT :limit
                        """, FindingEmbeddingEntity.class)
                .setParameter("excludeScanId", excludeScanId)
                .setParameter("vector", vectorLiteral)
                .setParameter("limit", limit)
                .getResultList();
    }

    /**
     * Converts a float[] to the pgvector literal format: '[0.1,0.2,...]'
     */
    private String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}