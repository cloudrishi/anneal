package com.rish.anneal.api.mapper;

import com.rish.anneal.api.dto.FindingDto;
import com.rish.anneal.api.dto.ScanResponse;
import com.rish.anneal.api.model.LlmProvider;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.llm.model.EnrichedFix;

import java.util.Comparator;
import java.util.List;

/**
 * Maps domain objects to API DTOs.
 * Stateless — no dependencies, pure transformation logic.
 */
public class ScanMapper {

    /**
     * Maps a ScanResult and its per-boundary scores to a ScanResponse DTO.
     */
    public static ScanResponse toResponse(
            ScanResult result,
            RiskScoreCalculator calculator) {

        List<FindingDto> findings = result.getFindings().stream()
                .sorted(Comparator
                        .comparing((Finding f) -> f.getSeverity().ordinal())
                        .thenComparingDouble(f -> -f.getConfidence()))
                .map(ScanMapper::toFindingDto)
                .toList();

        List<ScanResponse.BoundaryScoreDto> boundaryScores = calculator
                .calculatePerBoundary(result.getFindings())
                .stream()
                .map(bs -> new ScanResponse.BoundaryScoreDto(
                        bs.from().toString(),
                        bs.to().toString(),
                        bs.score(),
                        bs.band().name(),
                        bs.findingCount()
                ))
                .toList();

        RiskScoreCalculator.RiskBand band = calculator.band(result.getRiskScore());

        return new ScanResponse(
                result.getScanId(),
                result.getRepoPath(),
                result.getDetectedVersion().toString(),
                result.getTargetVersion().toString(),
                result.getRiskScore(),
                band.name(),
                result.getPhase().name(),
                result.getFilesScanned(),
                result.getFilesWithFindings(),
                findings,
                boundaryScores,
                result.getScannedAt().toString()
        );
    }

    /**
     * Maps a single Finding domain object to a FindingDto.
     * referenceUrl is denormalized onto Finding at detection time — no rule lookup needed here.
     */
    public static FindingDto toFindingDto(Finding finding) {
        String fixType = finding.getFixSuggestion() != null
                ? finding.getFixSuggestion().getFixType().name()
                : null;

        String suggestedCode = finding.getFixSuggestion() != null
                ? finding.getFixSuggestion().getSuggestedCode()
                : null;

        boolean autoApplicable = finding.getFixSuggestion() != null
                && finding.getFixSuggestion().isAutoApplicable();

        return new FindingDto(
                finding.getFindingId(),
                finding.getRuleId(),
                finding.getRuleName(),
                finding.getCategory().name(),
                finding.getSeverity().name(),
                finding.getEffort().name(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getOriginalCode(),
                finding.getDescription(),
                finding.getConfidence(),
                finding.getAffectsVersion().toString(),
                fixType,
                suggestedCode,
                autoApplicable,
                finding.getStatus().name(),
                finding.getReferenceUrl(),
                null,   // llmExplanation
                null,   // llmProvider
                null    // llmModel
        );
    }

    /**
     * Maps a single Finding with a pre-resolved EnrichedFix to a FindingDto.
     * Used by ScanResource after enrichAll() returns.
     * Passing null fix is safe — all three LLM fields will be null.
     */
    public static FindingDto toFindingDto(Finding finding, EnrichedFix fix) {
        String fixType = finding.getFixSuggestion() != null
                ? finding.getFixSuggestion().getFixType().name()
                : null;
        String suggestedCode = finding.getFixSuggestion() != null
                ? finding.getFixSuggestion().getSuggestedCode()
                : null;
        boolean autoApplicable = finding.getFixSuggestion() != null
                && finding.getFixSuggestion().isAutoApplicable();

        String llmExplanation = fix != null ? fix.explanation() : null;
        LlmProvider llmProvider = fix != null
                ? switch (fix.model()) {
            case com.rish.anneal.llm.model.LlmModel.Anthropic a -> LlmProvider.ANTHROPIC;
            case com.rish.anneal.llm.model.LlmModel.Ollama o -> LlmProvider.OLLAMA;
        }
                : null;
        String llmModel = fix != null ? fix.model().modelName() : null;

        return new FindingDto(
                finding.getFindingId(),
                finding.getRuleId(),
                finding.getRuleName(),
                finding.getCategory().name(),
                finding.getSeverity().name(),
                finding.getEffort().name(),
                finding.getFilePath(),
                finding.getLineNumber(),
                finding.getOriginalCode(),
                finding.getDescription(),
                finding.getConfidence(),
                finding.getAffectsVersion().toString(),
                fixType,
                suggestedCode,
                autoApplicable,
                finding.getStatus().name(),
                finding.getReferenceUrl(),
                llmExplanation,
                llmProvider,
                llmModel
        );
    }

    /**
     * Maps a ScanResultEntity to a ScanSummaryDto for the list view.
     */
    public static com.rish.anneal.api.dto.ScanSummaryDto toSummary(
            com.rish.anneal.store.entity.ScanResultEntity entity,
            long findingCount) {
        return new com.rish.anneal.api.dto.ScanSummaryDto(
                entity.scanId,
                entity.repoPath,
                entity.detectedVersion,
                entity.targetVersion,
                entity.riskScore,
                entity.riskBand,
                entity.phase,
                entity.filesScanned,
                entity.filesWithFindings,
                findingCount,
                entity.scannedAt.toString()
        );
    }

    /**
     * Maps a ScanResultEntity and its FindingEntities to a ScanResponse.
     * Note: requires FindingEntity.referenceUrl column — see V3 Flyway migration.
     */
    public static com.rish.anneal.api.dto.ScanResponse fromEntity(
            com.rish.anneal.store.entity.ScanResultEntity entity,
            java.util.List<com.rish.anneal.store.entity.FindingEntity> findings) {

        var findingDtos = findings.stream()
                .map(f -> new FindingDto(
                        f.findingId, f.ruleId, f.ruleName, f.category,
                        f.severity, f.effort, f.filePath, f.lineNumber,
                        f.originalCode, f.description, f.confidence,
                        f.affectsVersion, f.fixType, f.suggestedCode,
                        f.autoApplicable, f.status, f.referenceUrl,
                        null, // llmExplanation not stored
                        null, // llmProvider not stored
                        null // llmModel not stored
                ))
                .toList();

        return new com.rish.anneal.api.dto.ScanResponse(
                entity.scanId, entity.repoPath, entity.detectedVersion,
                entity.targetVersion, entity.riskScore, entity.riskBand,
                entity.phase, entity.filesScanned, entity.filesWithFindings,
                findingDtos, List.of(), entity.scannedAt.toString()
        );
    }
}