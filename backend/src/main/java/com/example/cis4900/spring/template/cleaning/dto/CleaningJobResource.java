package com.example.cis4900.spring.template.cleaning.dto;

import java.time.Instant;
import com.example.cis4900.spring.template.cleaning.model.CleaningRunSummary;

public record CleaningJobResource(
    String jobId,
    String status,
    Instant createdAt,
    CleaningRunSummary summary
) {}
