package com.rish.anneal.store.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;

/**
 * Panache entity for the anneal.scan_results table.
 * Stores the top-level metadata for each scan.
 */
@Entity
@Table(schema = "anneal", name = "scan_results")
public class ScanResultEntity extends PanacheEntity {

    @Column(name = "scan_id", nullable = false, unique = true)
    public String scanId;

    @Column(name = "repo_path", nullable = false)
    public String repoPath;

    @Column(name = "detected_version", nullable = false)
    public String detectedVersion;

    @Column(name = "target_version", nullable = false)
    public String targetVersion;

    @Column(name = "risk_score", nullable = false)
    public int riskScore;

    @Column(name = "risk_band", nullable = false)
    public String riskBand;

    @Column(name = "phase", nullable = false)
    public String phase;

    @Column(name = "files_scanned", nullable = false)
    public int filesScanned;

    @Column(name = "files_with_findings", nullable = false)
    public int filesWithFindings;

    @Column(name = "scanned_at", nullable = false)
    public Instant scannedAt;

    @OneToMany(mappedBy = "scanResult", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    public List<FindingEntity> findings;

    public static ScanResultEntity findByScanId(String scanId) {
        return find("scanId", scanId).firstResult();
    }
}
