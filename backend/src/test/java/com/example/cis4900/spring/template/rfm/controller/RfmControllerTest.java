package com.example.cis4900.spring.template.rfm.controller;

import com.example.cis4900.spring.template.rfm.service.RfmService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RfmController.class)
public class RfmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RfmService rfmService;

    @Test
    void testGetScatterPlotData_ReturnsOk() throws Exception {
        // mock service to return an empty list
        when(rfmService.getRfmData(any(), any(), anyString())).thenReturn(List.of());

        // Test GET request with required ISO date parameters
        mockMvc.perform(get("/api/rfm/scatter-plot")
                .param("startDate", "2020-01-01T00:00:00")
                .param("endDate", "2021-01-01T00:00:00")
                .param("country", "United Kingdom"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetScatterPlotData_MissingParams_ReturnsBadRequest() throws Exception {
        // missing startDate should trigger a 400 error
        mockMvc.perform(get("/api/rfm/scatter-plot")
                .param("endDate", "2021-01-01T00:00:00"))
                .andExpect(status().isBadRequest());
    }
}
