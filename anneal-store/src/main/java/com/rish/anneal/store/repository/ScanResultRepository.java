package com.rish.anneal.store.repository;

import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.store.entity.FindingEntity;
import com.rish.anneal.store.entity.ScanResultEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for persisting and retrieving scan results.
 * Translates between domain objects and Panache entities.
 */
@ApplicationScoped
public class ScanResultRepository {

    private final RiskScoreCalculator riskScoreCalculator = new RiskScoreCalculator();

    /**
     * Persists a ScanResult and all its findings to the database.
     */
    @Transactional
    public void save(ScanResult result) {
        ScanResultEntity entity = toEntity(result);
        entity.persist();

        for (Finding finding : result.getFindings()) {
            FindingEntity findingEntity = toFindingEntity(finding, entity);
            findingEntity.persist();
        }
    }

    /**
     * Finds a scan result by its scanId.
     */
    public Optional<ScanResultEntity> findByScanId(String scanId) {
        return Optional.ofNullable(ScanResultEntity.findByScanId(scanId));
    }

    /**
     * Returns all scan results ordered by most recent first.
     */
    public List<ScanResultEntity> findAll() {
        return ScanResultEntity.list("ORDER BY scannedAt DESC");
    }

    /**
     * Returns the finding count for a given scanId.
     */
    public long countFindings(String scanId) {
        return FindingEntity.count("scanResult.scanId", scanId);
    }

    /**
     * Returns all findings for a given scanId.
     */
    public List<FindingEntity> findingsByScanId(String scanId) {
        return FindingEntity.findByScanId(scanId);
    }

    private ScanResultEntity toEntity(ScanResult result) {
        RiskScoreCalculator.RiskBand band = riskScoreCalculator.band(result.getRiskScore());

        ScanResultEntity entity = new ScanResultEntity();
        entity.scanId = result.getScanId();
        entity.repoPath = result.getRepoPath();
        entity.detectedVersion = result.getDetectedVersion().toString();
        entity.targetVersion = result.getTargetVersion().toString();
        entity.riskScore = result.getRiskScore();
        entity.riskBand = band.name();
        entity.phase = result.getPhase().name();
        entity.filesScanned = result.getFilesScanned();
        entity.filesWithFindings = result.getFilesWithFindings();
        entity.scannedAt = result.getScannedAt();
        return entity;
    }

    private FindingEntity toFindingEntity(Finding finding, ScanResultEntity scanResult) {
        FindingEntity entity = new FindingEntity();
        entity.scanResult = scanResult;
        entity.findingId = finding.getFindingId();
        entity.ruleId = finding.getRuleId();
        entity.ruleName = finding.getRuleName();
        entity.category = finding.getCategory().name();
        entity.severity = finding.getSeverity().name();
        entity.effort = finding.getEffort().name();
        entity.filePath = finding.getFilePath();
        entity.lineNumber = finding.getLineNumber();
        entity.originalCode = finding.getOriginalCode();
        entity.description = finding.getDescription();
        entity.confidence = finding.getConfidence();
        entity.affectsVersion = finding.getAffectsVersion().toString();
        entity.status = finding.getStatus().name();

        if (finding.getFixSuggestion() != null) {
            entity.fixType = finding.getFixSuggestion().getFixType().name();
            entity.suggestedCode = finding.getFixSuggestion().getSuggestedCode();
            entity.autoApplicable = finding.getFixSuggestion().isAutoApplicable();
        }

        return entity;
    }
}
