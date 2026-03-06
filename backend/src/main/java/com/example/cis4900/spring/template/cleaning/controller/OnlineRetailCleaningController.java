package com.example.cis4900.spring.template.cleaning.controller;

import com.example.cis4900.spring.template.cleaning.dto.CreateCleaningJobRequest;
import com.example.cis4900.spring.template.cleaning.dto.CleaningJobResource;
import com.example.cis4900.spring.template.cleaning.model.CleaningRunSummary;
import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningPipelineService;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/cleaning-jobs")
public class OnlineRetailCleaningController {

    /**
     * Simple REST controller exposing synchronous cleaning job endpoints.
     *
     * Note: For this exercise the controller runs the cleaning pipeline synchronously
     * on POST and stores results in an in-memory map keyed by job id. In production
     * this would typically be an async job with persistence to a jobs table.
     */

    private final OnlineRetailCleaningPipelineService cleaningPipelineService;
    private final Map<String, CleaningJobResource> jobsById = new ConcurrentHashMap<>();

    public OnlineRetailCleaningController(OnlineRetailCleaningPipelineService cleaningPipelineService) {
        this.cleaningPipelineService = cleaningPipelineService;
    }

    @PostMapping
    public ResponseEntity<CleaningJobResource> createCleaningJob(
        @RequestBody(required = false) CreateCleaningJobRequest request
    ) {
        // Allow callers to override batch size for the run; null uses default.
        Integer batchSize = request == null ? null : request.batchSize();
        CleaningRunSummary summary = cleaningPipelineService.runCleaning(batchSize);

        String jobId = UUID.randomUUID().toString();
        CleaningJobResource jobResource = new CleaningJobResource(
            jobId,
            "COMPLETED",
            Instant.now(),
            summary
        );
        jobsById.put(jobId, jobResource);

        URI location = ServletUriComponentsBuilder
            .fromCurrentRequest()
            .path("/{jobId}")
            .buildAndExpand(jobId)
            .toUri();

        return ResponseEntity
            .created(location)
            .cacheControl(CacheControl.noStore())
            .body(jobResource);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<CleaningJobResource> getCleaningJob(@PathVariable String jobId) {
        CleaningJobResource jobResource = jobsById.get(jobId);
        if (jobResource == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .body(jobResource);
    }
}
