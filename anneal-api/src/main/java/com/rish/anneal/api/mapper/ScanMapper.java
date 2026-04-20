package com.rish.anneal.api.mapper;

import com.rish.anneal.api.dto.FindingDto;
import com.rish.anneal.api.dto.ScanResponse;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.ScanResult;

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
                null
        );
    }
}
