package com.rish.anneal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for PATCH /api/scans/{scanId}/findings/{findingId}.
 *
 * <p>Only {@code status} is updatable. The permitted values mirror
 * {@code Finding.FindingStatus} — validated at the API boundary so invalid
 * values are rejected with 400 before reaching the repository.
 *
 * @param status the new status — ACCEPTED, REJECTED, or DEFERRED.
 *               OPEN is intentionally excluded — findings start OPEN and
 *               the UI has no "reopen" action in Phase 3.
 */
public record StatusUpdateRequest(

        @NotBlank(message = "status is required")
        @Pattern(
                regexp = "ACCEPTED|REJECTED|DEFERRED",
                message = "status must be one of: ACCEPTED, REJECTED, DEFERRED"
        )
        String status
) {}
