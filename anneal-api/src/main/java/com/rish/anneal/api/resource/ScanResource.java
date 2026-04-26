package com.rish.anneal.api.resource;

import com.rish.anneal.api.dto.FindingDto;
import com.rish.anneal.api.dto.ScanRequest;
import com.rish.anneal.api.dto.ScanResponse;
import com.rish.anneal.api.dto.StatusUpdateRequest;
import com.rish.anneal.api.mapper.ScanMapper;
import com.rish.anneal.api.registry.RuleRegistry;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.engine.RuleEngine;
import com.rish.anneal.core.model.Finding;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.core.rule.MigrationRule;
import com.rish.anneal.core.scanner.BuildFileScanner;
import com.rish.anneal.core.scanner.CodebaseScanner;
import com.rish.anneal.core.scanner.VersionDetector;
import com.rish.anneal.llm.model.EnrichedFix;
import com.rish.anneal.llm.service.EmbeddingService;
import com.rish.anneal.llm.service.FixEnrichmentService;
import com.rish.anneal.store.repository.EmbeddingRepository;
import com.rish.anneal.store.repository.ScanResultRepository;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST resource for codebase scanning and migration analysis.
 * <p>
 * Endpoints:
 * GET  /api/health           — liveness check
 * POST /api/scan             — scan a Java repository
 * GET  /api/scans            — list all past scans
 * GET  /api/scans/{scanId}   — retrieve a specific scan with findings
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Scan", description = "Java codebase migration analysis")
public class ScanResource {

    private static final Logger log = Logger.getLogger(ScanResource.class);

    private final RuleRegistry ruleRegistry;
    private final ScanResultRepository repository;
    private final FixEnrichmentService enrichmentService;
    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final RiskScoreCalculator riskScoreCalculator = new RiskScoreCalculator();
    private final VersionDetector versionDetector = new VersionDetector();

    @Inject
    public ScanResource(RuleRegistry ruleRegistry,
                        ScanResultRepository repository,
                        FixEnrichmentService enrichmentService,
                        EmbeddingService embeddingService,
                        EmbeddingRepository embeddingRepository) {
        this.ruleRegistry = ruleRegistry;
        this.repository = repository;
        this.enrichmentService = enrichmentService;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
    }

    @GET
    @Path("/health")
    @Operation(summary = "Health check", description = "Returns OK if the service is running")
    public Response health() {
        return Response.ok("{\"status\":\"ok\",\"service\":\"anneal\"}").build();
    }

    @POST
    @Path("/scan")
    @Operation(
            summary = "Scan a Java repository",
            description = "Walks the repository, applies migration rules, and returns findings with risk score"
    )
    public Response scan(@Valid ScanRequest request) {

        java.nio.file.Path repoPath = Paths.get(request.repoPath());

        if (!repoPath.toFile().exists()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Path not found: " + request.repoPath() + "\"}")
                    .build();
        }

        if (!repoPath.toFile().isDirectory()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Path is not a directory: " + request.repoPath() + "\"}")
                    .build();
        }

        JavaVersion source = resolveSourceVersion(request, repoPath);
        if (source == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Could not detect Java version. " +
                            "Specify sourceVersion in the request (e.g. \\\"8\\\", \\\"11\\\", \\\"17\\\", \\\"21\\\")\"}")
                    .build();
        }

        JavaVersion target = JavaVersion.V25;
        List<MigrationRule> rules = ruleRegistry.rulesFor(source, target);

        // Index by ruleId for O(1) lookup in enrichAll()
        Map<String, MigrationRule> ruleById = rules.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MigrationRule::getRuleId,
                        java.util.function.Function.identity()
                ));

        CodebaseScanner scanner = new CodebaseScanner(
                new RuleEngine(),
                riskScoreCalculator,
                new BuildFileScanner()
        );

        ScanResult result = scanner.scan(repoPath, rules, source, target);
        repository.save(result);

        // --- LLM enrichment ---
        Map<String, EnrichedFix> enrichments = enrichmentService.enrichAll(result.getFindings(), ruleById);
        log.infof("Enriched %d/%d findings for scan %s",
                enrichments.size(), result.getFindings().size(), result.getScanId());

        // --- Embeddings ---
        // Embed each finding and persist to finding_embeddings for future similarity search.
        // Failures are logged and skipped — embedding is non-critical to scan correctness.
        for (Finding finding : result.getFindings()) {
            try {
                float[] vector = embeddingService.embed(finding);
                String embeddedText = embeddingService.embeddingText(finding);
                embeddingRepository.save(
                        finding.getFindingId(),
                        result.getScanId(),
                        finding.getRuleId(),
                        vector,
                        embeddedText
                );
            } catch (Exception e) {
                log.warnf("Embedding failed for finding %s: %s", finding.getFindingId(), e.getMessage());
            }
        }

        // --- Build response with enriched findings ---
        // Use overloaded toFindingDto(finding, explanation) to inject LLM explanation per finding.
        List<Finding> sortedFindings = result.getFindings().stream()
                .sorted(java.util.Comparator
                        .comparing((Finding f) -> f.getSeverity().ordinal())
                        .thenComparingDouble(f -> -f.getConfidence()))
                .toList();

        List<FindingDto> findingDtos = sortedFindings.stream()
                .map(f -> ScanMapper.toFindingDto(f, enrichments.get(f.getFindingId())))
                .toList();

        // Build boundary scores
        List<ScanResponse.BoundaryScoreDto> boundaryScores = riskScoreCalculator
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

        ScanResponse response = new ScanResponse(
                result.getScanId(),
                result.getRepoPath(),
                result.getDetectedVersion().toString(),
                result.getTargetVersion().toString(),
                result.getRiskScore(),
                riskScoreCalculator.band(result.getRiskScore()).name(),
                result.getPhase().name(),
                result.getFilesScanned(),
                result.getFilesWithFindings(),
                findingDtos,
                boundaryScores,
                result.getScannedAt().toString()
        );

        return Response.ok(response).build();
    }

    @GET
    @Path("/scans")
    @Operation(summary = "List all scans", description = "Returns all past scans, most recent first")
    public Response listScans() {
        var scans = repository.findAll().stream()
                .map(entity -> ScanMapper.toSummary(entity, repository.countFindings(entity.scanId)))
                .toList();
        return Response.ok(scans).build();
    }

    @GET
    @Path("/scans/{scanId}")
    @Operation(summary = "Get scan by ID", description = "Returns a specific scan with all findings")
    public Response getScan(@PathParam("scanId") String scanId) {
        return repository.findByScanId(scanId)
                .map(entity -> {
                    var findings = repository.findingsByScanId(scanId);
                    return Response.ok(ScanMapper.fromEntity(entity, findings)).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\":\"Scan not found: " + scanId + "\"}")
                        .build());
    }

    /**
     * Updates the status of a finding within a scan.
     *
     * <p>Permitted transitions: OPEN → ACCEPTED, OPEN → REJECTED, OPEN → DEFERRED.
     * The endpoint is idempotent — patching an already-accepted finding to ACCEPTED
     * is a no-op that returns 200.
     *
     * <p>Returns 404 if the scanId or findingId does not exist, or if the finding
     * does not belong to the given scan.
     */
    @PATCH
    @Path("/scans/{scanId}/findings/{findingId}")
    @Operation(
            summary = "Update finding status",
            description = "Accepts, rejects, or defers a finding. Status is persisted immediately."
    )
    public Response updateFindingStatus(
            @PathParam("scanId") String scanId,
            @PathParam("findingId") String findingId,
            @Valid StatusUpdateRequest request
    ) {
        boolean updated = repository.updateFindingStatus(scanId, findingId, request.status());

        if (!updated) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"error\":\"Finding not found: " + findingId + " in scan: " + scanId + "\"}")
                    .build();
        }

        log.infof("Finding %s status updated to %s in scan %s", findingId, request.status(), scanId);
        return Response.ok("{\"findingId\":\"" + findingId + "\",\"status\":\"" + request.status() + "\"}").build();
    }


    private JavaVersion resolveSourceVersion(ScanRequest request,
                                             java.nio.file.Path repoPath) {
        if (request.sourceVersion() != null && !request.sourceVersion().isBlank()) {
            try {
                return JavaVersion.fromInt(Integer.parseInt(request.sourceVersion().trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Optional<JavaVersion> detected = versionDetector.detect(repoPath);
        return detected.orElse(null);
    }
}
