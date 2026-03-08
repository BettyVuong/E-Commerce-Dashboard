package com.example.cis4900.spring.template.cleaning.controller;

import com.example.cis4900.spring.template.cleaning.dto.CleanedRetailDataItem;
import com.example.cis4900.spring.template.cleaning.dto.DirtyRetailDataItem;
import com.example.cis4900.spring.template.cleaning.dto.ManualReviewItem;
import com.example.cis4900.spring.template.cleaning.dto.PagedResponse;
import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningQueryService;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * REST endpoints for paged browsing of cleaned and manual-review data.
 */
@RestController
@RequestMapping("/api/cleaning-data")
public class OnlineRetailCleaningDataController {

    private static final int DEFAULT_SIZE = 15;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(15, 25, 50, 100);

    private final OnlineRetailCleaningQueryService cleaningQueryService;

    public OnlineRetailCleaningDataController(OnlineRetailCleaningQueryService cleaningQueryService) {
        this.cleaningQueryService = cleaningQueryService;
    }

    /**
     * Reads one page of cleaned records for frontend table rendering.
     */
    @GetMapping("/cleaned")
    public ResponseEntity<PagedResponse<CleanedRetailDataItem>> getCleanedDataPage(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size
    ) {
        validatePage(page);
        int resolvedSize = resolveSize(size);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(cleaningQueryService.getCleanedDataPage(page, resolvedSize));
    }

    /**
     * Reads one page of manual-review rows for frontend table rendering.
     */
    @GetMapping("/manual-review")
    public ResponseEntity<PagedResponse<ManualReviewItem>> getManualReviewPage(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size,
        // Optional filter used by frontend Invalid tab to request only REJECTED rows.
        @RequestParam(required = false) String reviewStatus
    ) {
        validatePage(page);
        int resolvedSize = resolveSize(size);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(cleaningQueryService.getManualReviewPage(page, resolvedSize, reviewStatus));
    }

    /**
     * Reads one page of original uploaded rows for frontend table rendering.
     */
    @GetMapping("/dirty")
    public ResponseEntity<PagedResponse<DirtyRetailDataItem>> getDirtyDataPage(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size
    ) {
        validatePage(page);
        int resolvedSize = resolveSize(size);

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(cleaningQueryService.getDirtyDataPage(page, resolvedSize));
    }

    private static void validatePage(int page) {
        // Keep paging deterministic by rejecting negative offsets.
        if (page < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "page must be >= 0");
        }
    }

    private static int resolveSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_SIZE;
        }
        // Restrict page size to UI-supported options.
        if (!ALLOWED_SIZES.contains(requestedSize)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "size must be one of 15, 25, 50, 100"
            );
        }
        return requestedSize;
    }
}
