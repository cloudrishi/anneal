package com.rish.anneal.api.resource;

import com.rish.anneal.api.dto.ScanRequest;
import com.rish.anneal.api.dto.ScanResponse;
import com.rish.anneal.api.mapper.ScanMapper;
import com.rish.anneal.api.registry.RuleRegistry;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.engine.RuleEngine;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.core.scanner.BuildFileScanner;
import com.rish.anneal.core.scanner.CodebaseScanner;
import com.rish.anneal.core.scanner.VersionDetector;
import com.rish.anneal.store.repository.ScanResultRepository;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.nio.file.Paths;
import java.util.List;
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

    private final RuleRegistry ruleRegistry;
    private final ScanResultRepository repository;
    private final RiskScoreCalculator riskScoreCalculator = new RiskScoreCalculator();
    private final VersionDetector versionDetector = new VersionDetector();

    @Inject
    public ScanResource(RuleRegistry ruleRegistry, ScanResultRepository repository) {
        this.ruleRegistry = ruleRegistry;
        this.repository = repository;
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

        CodebaseScanner scanner = new CodebaseScanner(
                new RuleEngine(),
                riskScoreCalculator,
                new BuildFileScanner()
        );

        ScanResult result = scanner.scan(repoPath, rules, source, target);
        repository.save(result);
        ScanResponse response = ScanMapper.toResponse(result, riskScoreCalculator);

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
