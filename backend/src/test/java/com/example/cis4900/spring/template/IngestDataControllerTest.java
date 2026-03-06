package com.example.cis4900.spring.template;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import com.example.cis4900.spring.template.ingest.service.IngestDataService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.example.cis4900.spring.template.ingest.controller.IngestDataController;

@WebMvcTest(IngestDataController.class)
public class IngestDataControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private IngestDataService ingestDataService;

    //test controllers through mock http requests
    @Test
    void testUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n536374,82345,WHITE HANGING HEART T-LIGHT HOLDER,6,12/1/2010 8:26,2.55,17850.0,United Kingdom".getBytes());
        
        doNothing().when(ingestDataService).processFile(any(org.springframework.web.multipart.MultipartFile.class));
        mockMvc.perform(multipart("/api/ingest/upload").file(file))
                .andExpect(status().isOk());
        verify(ingestDataService, times(1)).processFile(any(org.springframework.web.multipart.MultipartFile.class));
    }
}
