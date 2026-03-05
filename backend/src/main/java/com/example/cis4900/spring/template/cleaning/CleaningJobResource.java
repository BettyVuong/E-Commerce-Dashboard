package com.example.cis4900.spring.template.cleaning;

import java.time.Instant;

public record CleaningJobResource(
    String jobId,
    String status,
    Instant createdAt,
    CleaningRunSummary summary
) {
}
