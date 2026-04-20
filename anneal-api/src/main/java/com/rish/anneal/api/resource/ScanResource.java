package com.rish.anneal.api.resource;

import com.rish.anneal.api.dto.ScanRequest;
import com.rish.anneal.api.dto.ScanResponse;
import com.rish.anneal.api.mapper.ScanMapper;
import com.rish.anneal.api.registry.RuleRegistry;
import com.rish.anneal.core.engine.RiskScoreCalculator;
import com.rish.anneal.core.model.JavaVersion;
import com.rish.anneal.core.model.MigrationRule;
import com.rish.anneal.core.model.ScanResult;
import com.rish.anneal.core.scanner.BuildFileScanner;
import com.rish.anneal.core.scanner.CodebaseScanner;
import com.rish.anneal.core.scanner.VersionDetector;
import com.rish.anneal.core.engine.RuleEngine;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * REST resource for codebase scanning and migration analysis.
 *
 * Endpoints:
 *   GET  /api/health  — liveness check
 *   POST /api/scan    — scan a Java repository and return findings
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Scan", description = "Java codebase migration analysis")
public class ScanResource {

    private final RuleRegistry ruleRegistry;
    private final RiskScoreCalculator riskScoreCalculator;
    private final VersionDetector versionDetector;

    @Inject
    public ScanResource(RuleRegistry ruleRegistry,
                        RiskScoreCalculator riskScoreCalculator,
                        VersionDetector versionDetector) {
        this.ruleRegistry = ruleRegistry;
        this.riskScoreCalculator = riskScoreCalculator;
        this.versionDetector = versionDetector;
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

        // Detect or parse source version
        JavaVersion source = resolveSourceVersion(request, repoPath);
        if (source == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Could not detect Java version. " +
                            "Specify sourceVersion in the request (e.g. \\\"8\\\", \\\"11\\\", \\\"17\\\", \\\"21\\\")\"}")
                    .build();
        }

        JavaVersion target = JavaVersion.V25;

        // Get applicable rules for this boundary
        List<MigrationRule> rules = ruleRegistry.rulesFor(source, target);

        // Run the scan
        CodebaseScanner scanner = new CodebaseScanner(
                new RuleEngine(),
                riskScoreCalculator,
                new BuildFileScanner()
        );

        ScanResult result = scanner.scan(repoPath, rules, source, target);

        ScanResponse response = ScanMapper.toResponse(result, riskScoreCalculator);

        return Response.ok(response).build();
    }

    private JavaVersion resolveSourceVersion(ScanRequest request,
                                             java.nio.file.Path repoPath) {
        // If caller specified a version, use it
        if (request.sourceVersion() != null && !request.sourceVersion().isBlank()) {
            try {
                return JavaVersion.fromInt(Integer.parseInt(request.sourceVersion().trim()));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // Otherwise auto-detect from build file
        Optional<JavaVersion> detected = versionDetector.detect(repoPath);
        return detected.orElse(null);
    }
}
