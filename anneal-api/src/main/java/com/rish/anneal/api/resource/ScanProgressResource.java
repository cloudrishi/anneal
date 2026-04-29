package com.rish.anneal.api.resource;

import com.rish.anneal.api.dto.ScanResponse;
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
import com.rish.anneal.core.scanner.ScanProgressEvent;
import com.rish.anneal.core.scanner.VersionDetector;
import com.rish.anneal.llm.model.LlmModel;
import com.rish.anneal.llm.service.EmbeddingService;
import com.rish.anneal.llm.service.FixEnrichmentService;
import com.rish.anneal.store.repository.EmbeddingRepository;
import com.rish.anneal.store.repository.ScanResultRepository;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * SSE streaming endpoint for scan progress.
 *
 * <h2>Why a separate resource from ScanResource</h2>
 * SSE requires a GET request — browsers cannot open an {@code EventSource} to a POST.
 * The existing {@code POST /api/scan} stays untouched for curl and programmatic access.
 * The UI uses this streaming endpoint. Both paths call the same scan logic.
 *
 * <h2>Flow</h2>
 * <pre>
 * GET /api/scan/stream?repoPath=...&sourceVersion=8
 *   -> validate path and version
 *   -> open SSE connection (text/event-stream)
 *   -> run CodebaseScanner with progress callback on a virtual thread
 *      each file emits a FILE event: {"type":"FILE","file":"FieldUtils.java","filesScanned":47,"totalFiles":259}
 *   -> on COMPLETE, persist result, fire async enrichment, emit DONE event with full ScanResponse JSON
 *   -> on ERROR, emit ERROR event and close
 * </pre>
 *
 * <h2>Sentinel value</h2>
 * A {@code BlockingQueue} bridges the scanning thread and the Mutiny stream.
 * A sentinel object ({@code DONE_SENTINEL}) signals end-of-stream so the
 * {@code Multi} knows when to complete.
 */
@Path("/api")
@Tag(name = "Scan", description = "Java codebase migration analysis")
public class ScanProgressResource {

    private static final Logger log = Logger.getLogger(ScanProgressResource.class);

    /**
     * Sentinel object placed on the queue to signal stream completion.
     */
    private static final String DONE_SENTINEL = "__DONE__";

    private final RuleRegistry ruleRegistry;
    private final ScanResultRepository repository;
    private final FixEnrichmentService enrichmentService;
    private final EmbeddingService embeddingService;
    private final EmbeddingRepository embeddingRepository;
    private final RiskScoreCalculator riskScoreCalculator = new RiskScoreCalculator();
    private final VersionDetector versionDetector = new VersionDetector();

    @Inject
    public ScanProgressResource(RuleRegistry ruleRegistry,
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

    // ─── Endpoint ─────────────────────────────────────────────────────────────

    /**
     * Streams scan progress as Server-Sent Events.
     *
     * <p>The frontend opens an {@code EventSource} to this URL. Events arrive
     * as each Java file is scanned. The final event carries the full
     * {@code ScanResponse} JSON so the frontend has everything it needs
     * without a separate round-trip.
     *
     * <p>Event shapes:
     * <pre>
     * data: {"type":"FILE","file":"FieldUtils.java","filesScanned":47,"totalFiles":259,"findingCount":7}
     * data: {"type":"DONE","scanId":"...","riskScore":99,...}   // full ScanResponse
     * data: {"type":"ERROR","message":"Path not found: ..."}
     * </pre>
     *
     * @param repoPath      absolute path to the repository to scan
     * @param sourceVersion optional Java version string e.g. "8", "11" — auto-detected if absent
     */
    @GET
    @Path("/scan/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Stream scan progress",
            description = "Opens an SSE connection and streams file-by-file scan progress. " +
                    "Final event contains the full scan result."
    )
    public Multi<String> stream(
            @QueryParam("repoPath") String repoPath,
            @QueryParam("sourceVersion") String sourceVersion
    ) {
        // Validate up front — emit ERROR and complete immediately if invalid
        if (repoPath == null || repoPath.isBlank()) {
            return Multi.createFrom().items(
                    errorEvent("repoPath query parameter is required"));
        }

        java.nio.file.Path path = Paths.get(repoPath);

        if (!path.toFile().exists()) {
            return Multi.createFrom().items(
                    errorEvent("Path not found: " + repoPath));
        }

        if (!path.toFile().isDirectory()) {
            return Multi.createFrom().items(
                    errorEvent("Path is not a directory: " + repoPath));
        }

        JavaVersion source = resolveSourceVersion(sourceVersion, path);
        if (source == null) {
            return Multi.createFrom().items(
                    errorEvent("Could not detect Java version. Specify sourceVersion e.g. 8, 11, 17, 21"));
        }

        // ── Build the stream ──────────────────────────────────────────────────

        // Queue bridges the scanning thread → Mutiny stream.
        // Capacity 512 — enough for any realistic codebase; scanner blocks if UI is slow.
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(512);

        // Run scan on a virtual thread — does not block the event loop
        Thread.ofVirtual().name("anneal-scan-stream").start(() ->
                runScan(path, source, queue));

        // Drain the queue into the SSE stream until sentinel is seen
        return Multi.createFrom().generator(
                () -> queue,
                (q, emitter) -> {
                    try {
                        String item = q.take();
                        if (DONE_SENTINEL.equals(item)) {
                            emitter.complete();
                        } else {
                            emitter.emit(item);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        emitter.fail(e);
                    }
                    return q;
                }
        );
    }

    // ─── Scan orchestration ───────────────────────────────────────────────────

    /**
     * Runs the full scan pipeline on a background virtual thread, pushing
     * JSON strings into the queue as progress events.
     *
     * <p>On completion, persists the result, fires async enrichment, and
     * pushes the final DONE event (full ScanResponse JSON) followed by the
     * sentinel to close the stream.
     */
    private void runScan(java.nio.file.Path repoPath,
                         JavaVersion source,
                         BlockingQueue<String> queue) {
        try {
            JavaVersion target = JavaVersion.V25;
            List<MigrationRule> rules = ruleRegistry.rulesFor(source, target);

            Map<String, MigrationRule> ruleById = rules.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            MigrationRule::getRuleId,
                            java.util.function.Function.identity()
                    ));

            CodebaseScanner scanner = new CodebaseScanner(
                    new RuleEngine(), riskScoreCalculator, new BuildFileScanner());

            // Run scan with progress callback — each file pushes a FILE event
            ScanResult result = scanner.scan(repoPath, rules, source, target, event -> {
                if (event.type() == ScanProgressEvent.Type.FILE) {
                    queue.offer(fileEvent(
                            event.file(),
                            event.filesScanned(),
                            event.totalFiles(),
                            event.findingCount()
                    ));
                }
                // COMPLETE is handled below after persist — don't emit it here
            });

            // Persist
            repository.save(result);

            // Async enrichment — fire and forget
            enrichmentService.enrichAllAsync(result.getFindings(), ruleById, fix -> {
                String provider = switch (fix.model()) {
                    case LlmModel.Anthropic a -> "ANTHROPIC";
                    case LlmModel.Ollama o -> "OLLAMA";
                };
                repository.updateFindingEnrichment(
                        fix.findingId(), fix.explanation(), provider, fix.model().modelName());
            });

            // Embeddings
            for (Finding finding : result.getFindings()) {
                try {
                    float[] vector = embeddingService.embed(finding);
                    embeddingRepository.save(
                            finding.getFindingId(), result.getScanId(),
                            finding.getRuleId(), vector,
                            embeddingService.embeddingText(finding));
                } catch (Exception e) {
                    log.warnf("Embedding failed for finding %s: %s",
                            finding.getFindingId(), e.getMessage());
                }
            }

            // Build response DTO
            List<Finding> sorted = result.getFindings().stream()
                    .sorted(java.util.Comparator
                            .comparing((Finding f) -> f.getSeverity().ordinal())
                            .thenComparingDouble(f -> -f.getConfidence()))
                    .toList();

            List<com.rish.anneal.api.dto.FindingDto> findingDtos = sorted.stream()
                    .map(f -> ScanMapper.toFindingDto(f, null))
                    .toList();

            List<ScanResponse.BoundaryScoreDto> boundaryScores = riskScoreCalculator
                    .calculatePerBoundary(result.getFindings()).stream()
                    .map(bs -> new ScanResponse.BoundaryScoreDto(
                            bs.from().toString(), bs.to().toString(),
                            bs.score(), bs.band().name(), bs.findingCount()))
                    .toList();

            ScanResponse response = new ScanResponse(
                    result.getScanId(), result.getRepoPath(),
                    result.getDetectedVersion().toString(), result.getTargetVersion().toString(),
                    result.getRiskScore(), riskScoreCalculator.band(result.getRiskScore()).name(),
                    result.getPhase().name(), result.getFilesScanned(), result.getFilesWithFindings(),
                    findingDtos, boundaryScores, result.getScannedAt().toString());

            // Emit DONE with full response
            queue.offer(doneEvent(response));

        } catch (Exception e) {
            log.errorf("Scan stream failed: %s", e.getMessage());
            queue.offer(errorEvent("Scan failed: " + e.getMessage()));
        } finally {
            queue.offer(DONE_SENTINEL);
        }
    }

    // ─── SSE event builders ───────────────────────────────────────────────────

    private static String fileEvent(String file, int filesScanned,
                                    int totalFiles, int findingCount) {
        return """
                {"type":"FILE","file":"%s","filesScanned":%d,"totalFiles":%d,"findingCount":%d}"""
                .formatted(escape(file), filesScanned, totalFiles, findingCount);
    }

    private static String doneEvent(ScanResponse response) {
        // Inline the key fields — avoids a full JSON serializer dependency in this class
        // ScanResponse is serialized by the existing Jackson/Jsonb config in anneal-api
        return """
                {"type":"DONE","scanId":"%s","riskScore":%d,"riskBand":"%s",\
                "filesScanned":%d,"filesWithFindings":%d,"findingCount":%d}"""
                .formatted(
                        response.scanId(), response.riskScore(), response.riskBand(),
                        response.filesScanned(), response.filesWithFindings(),
                        response.findings() != null ? response.findings().size() : 0);
    }

    private static String errorEvent(String message) {
        return """
                {"type":"ERROR","message":"%s"}""".formatted(escape(message));
    }

    /**
     * Escapes double quotes and backslashes for inline JSON string values.
     */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private JavaVersion resolveSourceVersion(String sourceVersion,
                                             java.nio.file.Path repoPath) {
        if (sourceVersion != null && !sourceVersion.isBlank()) {
            try {
                return JavaVersion.fromInt(Integer.parseInt(sourceVersion.trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return versionDetector.detect(repoPath).orElse(null);
    }
}
