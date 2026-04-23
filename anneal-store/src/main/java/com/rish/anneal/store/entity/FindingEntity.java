package com.rish.anneal.store.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Panache entity for the anneal.findings table.
 * Stores individual rule violation findings from a scan.
 */
@Entity
@Table(schema = "anneal", name = "findings")
public class FindingEntity extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_id", referencedColumnName = "scan_id", nullable = false)
    public ScanResultEntity scanResult;

    @Column(name = "finding_id", nullable = false, unique = true)
    public String findingId;

    @Column(name = "rule_id", nullable = false)
    public String ruleId;

    @Column(name = "rule_name", nullable = false)
    public String ruleName;

    @Column(name = "category", nullable = false)
    public String category;

    @Column(name = "severity", nullable = false)
    public String severity;

    @Column(name = "effort", nullable = false)
    public String effort;

    @Column(name = "file_path", nullable = false)
    public String filePath;

    @Column(name = "line_number")
    public int lineNumber;

    @Column(name = "original_code", columnDefinition = "TEXT")
    public String originalCode;

    @Column(name = "description", columnDefinition = "TEXT")
    public String description;

    @Column(name = "confidence")
    public float confidence;

    @Column(name = "affects_version")
    public String affectsVersion;

    @Column(name = "fix_type")
    public String fixType;

    @Column(name = "suggested_code", columnDefinition = "TEXT")
    public String suggestedCode;

    @Column(name = "auto_applicable")
    public boolean autoApplicable;

    @Column(name = "status", nullable = false)
    public String status;

    /**
     * Denormalized from MigrationRule at scan time. Nullable — older rows will be NULL.
     */
    @Column(name = "reference_url", length = 512)
    public String referenceUrl;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt = Instant.now();

    public static java.util.List<FindingEntity> findByScanId(String scanId) {
        return list("scanResult.scanId", scanId);
    }
}