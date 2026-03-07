package com.example.cis4900.spring.template;

import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningPipelineService;
import com.example.cis4900.spring.template.cleaning.service.OnlineRetailCleaningQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;

// Exclude datasource and Flyway auto-configuration so the test doesn't require a database
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.flywaydb.core.FlywayAutoConfiguration")
class TemplateApplicationTests {

    @MockBean
    private OnlineRetailCleaningPipelineService cleaningPipelineService;

    @MockBean
    private OnlineRetailCleaningQueryService cleaningQueryService;

    @Test
    void contextLoads() {
        // simple smoke test;
    }
}
