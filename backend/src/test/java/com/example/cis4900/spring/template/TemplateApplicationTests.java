package com.example.cis4900.spring.template;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Exclude datasource and Flyway auto-configuration so the test doesn't require a database
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.flywaydb.core.FlywayAutoConfiguration")
class TemplateApplicationTests {

    @Test
    void contextLoads() {
        // simple smoke test;
    }
}
