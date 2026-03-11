package com.example.cis4900.spring.template.rfm.service;

import com.example.cis4900.spring.template.rfm.model.RfmMetric;
import com.example.cis4900.spring.template.rfm.repository.RfmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RfmServiceTest {

    private RfmService rfmService;
    private RfmRepository rfmRepository;

    @BeforeEach
    void setUp() {
        // Mock the repository (Test Double)
        rfmRepository = mock(RfmRepository.class);
        // Inject the mock into the service
        rfmService = new RfmService(rfmRepository);
    }

    @Test
    void testGetRfmData_CallsRepositoryWithCorrectParams() {
        // Arrange
        LocalDateTime start = LocalDateTime.of(2023, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2023, 12, 31, 23, 59);
        String country = "Canada";
        List<RfmMetric> mockData = List.of(); // Mock interface projection result

        when(rfmRepository.findRfmStats(start, end, country)).thenReturn(mockData);

        // Act
        List<RfmMetric> result = rfmService.getRfmData(start, end, country);

        // Assert
        assertEquals(mockData, result);
        verify(rfmRepository, times(1)).findRfmStats(start, end, country);
    }
}
