package com.example.cis4900.spring.template.cleaning;

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

    private final OnlineRetailCleaningPipelineService cleaningPipelineService;
    private final Map<String, CleaningJobResource> jobsById = new ConcurrentHashMap<>();

    public OnlineRetailCleaningController(OnlineRetailCleaningPipelineService cleaningPipelineService) {
        this.cleaningPipelineService = cleaningPipelineService;
    }

    /**
     * Creates and executes a data-cleaning job for the online retail dataset.
     *
     * @param request optional payload with the requested batch size
     * @return created cleaning job resource
     */
    @PostMapping
    public ResponseEntity<CleaningJobResource> createCleaningJob(
        @RequestBody(required = false) CreateCleaningJobRequest request
    ) {
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

    /**
     * Reads a previously created cleaning job resource.
     *
     * @param jobId cleaning job resource identifier
     * @return cleaning job resource if found
     */
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
