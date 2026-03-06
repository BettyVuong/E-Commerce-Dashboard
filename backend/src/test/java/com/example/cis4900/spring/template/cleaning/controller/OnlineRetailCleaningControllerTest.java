package com.example.cis4900.spring.template.cleaning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.cis4900.spring.template.cleaning.dto.CleaningJobResource;
import com.example.cis4900.spring.template.cleaning.dto.CreateCleaningJobRequest;
import com.example.cis4900.spring.template.cleaning.model.CleaningRunSummary;
import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningPipelineService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

/**
 * Controller-level unit tests for the cleaning job endpoints.
 *
 * These tests exercise request/response shaping and job storage semantics.
 */
class OnlineRetailCleaningControllerTest {

    @Test
    void createAndGetJob_returnsCreatedAndRetrievableResource() {
        OnlineRetailCleaningPipelineService svc = Mockito.mock(OnlineRetailCleaningPipelineService.class);
        CleaningRunSummary summary = new CleaningRunSummary(1,1,0,0,0);
        Mockito.when(svc.runCleaning(Mockito.any())).thenReturn(summary);

        OnlineRetailCleaningController controller = new OnlineRetailCleaningController(svc);

        // Provide a mock servlet request so ServletUriComponentsBuilder can build a location
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));

        ResponseEntity<CleaningJobResource> created = controller.createCleaningJob(new CreateCleaningJobRequest(10));
        // Created response should include the job resource with status
        assertEquals(201, created.getStatusCodeValue());

        CleaningJobResource body = created.getBody();
        assertEquals("COMPLETED", body.status());

        ResponseEntity<CleaningJobResource> fetched = controller.getCleaningJob(body.jobId());
        assertEquals(200, fetched.getStatusCodeValue());
        assertEquals(body.jobId(), fetched.getBody().jobId());
    }
}
