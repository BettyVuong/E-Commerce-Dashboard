package com.example.cis4900.spring.template;

import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningPipelineService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import com.example.cis4900.spring.template.ingest.repository.DirtyDataRepository;
import org.springframework.boot.test.mock.mockito.MockBean;

// Exclude datasource and Flyway auto-configuration so the test doesn't require a database
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.flywaydb.core.FlywayAutoConfiguration")
class TemplateApplicationTests {

    // Mock the DirtyDataRepository to avoid needing a real database connection during tests
    @MockBean
    private DirtyDataRepository dirtyDataRepository;

    @MockBean
    private OnlineRetailCleaningPipelineService cleaningPipelineService;

    @Test
    void contextLoads() {
        // simple smoke test;
    }
}
