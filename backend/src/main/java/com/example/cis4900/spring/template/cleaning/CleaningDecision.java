package com.example.cis4900.spring.template.cleaning;

import java.util.List;

record CleaningDecision(
    CleanedRetailRecord cleanedRecord,
    CleaningReviewStatus reviewStatus,
    List<String> reviewReasons,
    List<String> validationErrors
) {

    boolean shouldInsertCleanedRecord() {
        return cleanedRecord != null && validationErrors.isEmpty();
    }

    boolean shouldInsertReviewRecord() {
        return reviewStatus != CleaningReviewStatus.NONE;
    }
}
