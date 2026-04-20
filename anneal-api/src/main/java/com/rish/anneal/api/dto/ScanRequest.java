package com.rish.anneal.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/scan.
 *
 * @param repoPath     absolute path to the repository root on disk
 * @param sourceVersion optional — if not provided, VersionDetector auto-detects
 *                      from pom.xml / build.gradle. Accepts "8", "11", "17", "21", "25".
 */
public record ScanRequest(
        @NotBlank(message = "repoPath is required")
        String repoPath,

        String sourceVersion
) {}
