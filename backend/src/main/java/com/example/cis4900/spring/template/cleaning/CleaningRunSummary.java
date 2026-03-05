package com.example.cis4900.spring.template.cleaning;

public record CleaningRunSummary(
    long totalRowsProcessed,
    long rowsInsertedIntoCleaned,
    long rowsFlaggedRejected,
    long rowsFlaggedAutoCleaned,
    long returnsDetected
) {
}
