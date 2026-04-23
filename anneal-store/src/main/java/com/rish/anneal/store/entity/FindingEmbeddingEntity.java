package com.rish.anneal.store.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Panache entity for the anneal.finding_embeddings table.
 * Stores the 384-dimensional AllMiniLmL6V2 embedding for each finding.
 * <p>
 * Separate from code_embeddings — that table is reserved for file-chunk RAG.
 * This table is per-finding: one row per Finding, keyed by findingId.
 * <p>
 * Cosine similarity search via pgvector <=> operator — see EmbeddingRepository.
 */
@Entity
@Table(schema = "anneal", name = "finding_embeddings")
public class FindingEmbeddingEntity extends PanacheEntity {

    /**
     * The finding this embedding was computed for. One-to-one with findings.finding_id.
     */
    @Column(name = "finding_id", nullable = false, unique = true)
    public String findingId;

    /**
     * The scan this embedding belongs to — used for cross-scan similarity queries.
     */
    @Column(name = "scan_id", nullable = false)
    public String scanId;

    /**
     * Rule that produced the finding — stored for filtering without joining findings.
     */
    @Column(name = "rule_id", nullable = false)
    public String ruleId;

    /**
     * 384-dimensional embedding vector.
     * Computed by AllMiniLmL6V2EmbeddingModel from: ruleId + severity + originalCode.
     * Stored as pgvector vector(384) — cosine similarity via <=> operator.
     */
    @Column(name = "embedding", columnDefinition = "vector(384)", nullable = false)
    public float[] embedding;

    /**
     * The text that was embedded — stored for debugging and audit.
     */
    @Column(name = "embedded_text", columnDefinition = "TEXT")
    public String embeddedText;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static FindingEmbeddingEntity findByFindingId(String findingId) {
        return find("findingId", findingId).firstResult();
    }

    public static java.util.List<FindingEmbeddingEntity> findByScanId(String scanId) {
        return list("scanId", scanId);
    }
}
