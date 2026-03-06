package com.example.cis4900.spring.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;

import com.example.cis4900.spring.template.ingest.model.DirtyData;
import com.example.cis4900.spring.template.ingest.repository.DirtyDataRepository;
import com.example.cis4900.spring.template.ingest.service.IngestDataService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.example.cis4900.spring.template.ingest.controller.IngestDataController;

class IngestDataServiceTest {
    private IngestDataService ingestDataService;
    private DirtyDataRepository dirtyDataRepository;

    @BeforeEach
    void setUp() {
        //mock repo
        dirtyDataRepository = mock(DirtyDataRepository.class);
        //inject mock repo into service
        ingestDataService = new IngestDataService(dirtyDataRepository);
    }

    //test that a valid CSV file is processed correctly and saved to the repository
    @Test
    void testCsvImport() throws Exception {
        String csvContent = 
            "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n"
            + "536374,82345,WHITE HANGING HEART T-LIGHT HOLDER,6,12/1/2010 8:26,2.55,17850.0,United Kingdom\n";
        // Create a mock MultipartFile with the CSV content
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
        
        //call service method to process the file
        ingestDataService.processFile(file);

        //capture what was processed and saved to the repository
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(dirtyDataRepository, times(1)).saveAll(captor.capture());
        //assert the captured data
        List<DirtyData> capturedData = captor.getValue();
        assertEquals(1, capturedData.size());
        assertEquals("536374", capturedData.get(0).getInvoice());
        assertEquals("82345", capturedData.get(0).getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", capturedData.get(0).getDescription());
        assertEquals("6", capturedData.get(0).getQuantity());
        assertEquals("12/1/2010 8:26", capturedData.get(0).getInvoiceDate());
        assertEquals("2.55", capturedData.get(0).getPrice());
        assertEquals("17850.0", capturedData.get(0).getCustomerID());
        assertEquals("United Kingdom", capturedData.get(0).getCountry());
    }

    //test that an unsupported file type throws the correct exception
    @Test
    void testUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "This is a test".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            ingestDataService.processFile(file)
        );
        assertEquals("Unsupported file type: test.txt", thrown.getMessage());
    }

    //test that an excel file is parsed and stored in db
    @Test
    void testExcelImport() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("testSheet");

        //create row 1 with header values
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Invoice");
        headerRow.createCell(1).setCellValue("StockCode");
        headerRow.createCell(2).setCellValue("Description");
        headerRow.createCell(3).setCellValue("Quantity");
        headerRow.createCell(4).setCellValue("InvoiceDate");
        headerRow.createCell(5).setCellValue("UnitPrice");
        headerRow.createCell(6).setCellValue("CustomerID");
        headerRow.createCell(7).setCellValue("Country");

        //create row 2 with data values
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("536374");
        dataRow.createCell(1).setCellValue("82345");
        dataRow.createCell(2).setCellValue("WHITE HANGING HEART T-LIGHT HOLDER");
        dataRow.createCell(3).setCellValue("6");
        dataRow.createCell(4).setCellValue("12/1/2010 8:26");
        dataRow.createCell(5).setCellValue("2.55");
        dataRow.createCell(6).setCellValue("17850.0");
        dataRow.createCell(7).setCellValue("United Kingdom");

        //write workbook to byte array
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        
        //mock MultipartFile with the Excel content
        MockMultipartFile mockFile = new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        
        //call service method to process the file
        ingestDataService.processFile(mockFile);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(dirtyDataRepository, times(1)).saveAll(captor.capture());
        //assert the captured data
        List<DirtyData> capturedData = captor.getValue();
        assertEquals(1, capturedData.size());
        assertEquals("536374", capturedData.get(0).getInvoice());
        assertEquals("82345", capturedData.get(0).getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", capturedData.get(0).getDescription());
        assertEquals("6", capturedData.get(0).getQuantity());
        assertEquals("12/1/2010 8:26", capturedData.get(0).getInvoiceDate());
        assertEquals("2.55", capturedData.get(0).getPrice());
        assertEquals("17850.0", capturedData.get(0).getCustomerID());
        assertEquals("United Kingdom", capturedData.get(0).getCountry());
    }

    //test getter and setter methods of the DirtyData class
    @Test
    void testDirtyDataGettersSetters() {
        DirtyData dirtyData = new DirtyData();
        dirtyData.setInvoice("536374");
        dirtyData.setStockCode("82345");
        dirtyData.setDescription("WHITE HANGING HEART T-LIGHT HOLDER");
        dirtyData.setQuantity("6");
        dirtyData.setInvoiceDate("12/1/2010 8:26");
        dirtyData.setPrice("2.55");
        dirtyData.setCustomerID("17850.0");
        dirtyData.setCountry("United Kingdom");
        assertEquals("536374", dirtyData.getInvoice());
        assertEquals("82345", dirtyData.getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", dirtyData.getDescription());
        assertEquals("6", dirtyData.getQuantity());
        assertEquals("12/1/2010 8:26", dirtyData.getInvoiceDate());
        assertEquals("2.55", dirtyData.getPrice());
        assertEquals("17850.0", dirtyData.getCustomerID());
        assertEquals("United Kingdom", dirtyData.getCountry());
    }

    //test overloaded constructor of the DirtyData class
    @Test
    void testDirtyDataConstructor() {
        DirtyData dirtyData = new DirtyData(
            "536374", // invoice
            "82345", // stockCode
            "WHITE HANGING HEART T-LIGHT HOLDER", // description
            "6", // quantity
            "12/1/2010 8:26", // invoiceDate
            "2.55", // price
            "17850.0", // customerID
            "United Kingdom" // country
        );
        assertEquals("536374", dirtyData.getInvoice());
        assertEquals("82345", dirtyData.getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", dirtyData.getDescription());
        assertEquals("6", dirtyData.getQuantity());
        assertEquals("12/1/2010 8:26", dirtyData.getInvoiceDate());
        assertEquals("2.55", dirtyData.getPrice());
        assertEquals("17850.0", dirtyData.getCustomerID());
        assertEquals("United Kingdom", dirtyData.getCountry()); 
    }

    //test that an empty file throws the correct exception
    @Test
    void testEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]);
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            ingestDataService.processFile(file)
        );
        assertEquals("File is empty", thrown.getMessage());
    }

    //test edge case where csv file has only headers and no data rows, should throw exception since no data to process
    @Test
    void testCsvFileWithOnlyHeaders() {
        String csvContent = "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n";
        MockMultipartFile file = new MockMultipartFile("file", "headers_only.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            ingestDataService.processFile(file)
        );
        assertEquals("CSV file is empty or only contains headers", thrown.getMessage());
    }

    //test edge case where excel file has only headers and no data rows, should throw exception since no data to process
    @Test
    void testExcelFileWithOnlyHeaders() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("testSheet"); 
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Invoice");
        headerRow.createCell(1).setCellValue("StockCode");
        headerRow.createCell(2).setCellValue("Description");
        headerRow.createCell(3).setCellValue("Quantity");
        headerRow.createCell(4).setCellValue("InvoiceDate");
        headerRow.createCell(5).setCellValue("UnitPrice");
        headerRow.createCell(6).setCellValue("CustomerID");
        headerRow.createCell(7).setCellValue("Country");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        MockMultipartFile file = new MockMultipartFile("file", "headers_only.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            ingestDataService.processFile(file)
        );
        assertEquals("Excel file is empty or only contains headers", thrown.getMessage());
    }

    //incomplete rows in csv file should be processed but missing values should be set to null
    @Test
    void testIncompleteRows() throws Exception {
        String csvContent = "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n536374,82345,WHITE HANGING HEART T-LIGHT HOLDER,6,12/1/2010 8:26,2.55,,\n";
        MockMultipartFile file = new MockMultipartFile("file", "incomplete.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));
        ingestDataService.processFile(file);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(dirtyDataRepository, times(1)).saveAll(captor.capture());
        List<DirtyData> capturedData = captor.getValue();
        assertEquals(1, capturedData.size());
        assertEquals("536374", capturedData.get(0).getInvoice());
        assertEquals("82345", capturedData.get(0).getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", capturedData.get(0).getDescription());
        assertEquals("6", capturedData.get(0).getQuantity());
        assertEquals("12/1/2010 8:26", capturedData.get(0).getInvoiceDate());
        assertEquals("2.55", capturedData.get(0).getPrice());
        assertEquals("", capturedData.get(0).getCustomerID());
        assertEquals("", capturedData.get(0).getCountry());
    }

    //test that a file with missing name throws the correct exception
    @Test
    void testFileWithMissingName() {
        MockMultipartFile file = new MockMultipartFile("file", null, "text/csv", "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n536374,82345,WHITE HANGING HEART T-LIGHT HOLDER,6,12/1/2010 8:26,2.55,17850.0,United Kingdom".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () ->
            ingestDataService.processFile(file)
        );
        assertEquals("File is empty", thrown.getMessage());
    }

    //test that rows in a xlsx file with missing values are processed but missing values are set to null
    @Test
    void testIncompleteRowsInExcel() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("testSheet");
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Invoice");
        headerRow.createCell(1).setCellValue("StockCode");
        headerRow.createCell(2).setCellValue("Description");
        headerRow.createCell(3).setCellValue("Quantity");
        headerRow.createCell(4).setCellValue("InvoiceDate");
        headerRow.createCell(5).setCellValue("UnitPrice");
        headerRow.createCell(6).setCellValue("CustomerID");
        headerRow.createCell(7).setCellValue("Country");
        Row dataRow = sheet.createRow(1);
        dataRow.createCell(0).setCellValue("536374");
        dataRow.createCell(1).setCellValue("82345");
        dataRow.createCell(2).setCellValue("WHITE HANGING HEART T-LIGHT HOLDER");
        dataRow.createCell(3).setCellValue("6");
        dataRow.createCell(4).setCellValue("12/1/2010 8:26");
        dataRow.createCell(5).setCellValue("2.55");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        workbook.write(output);
        workbook.close();
        MockMultipartFile file = new MockMultipartFile("file", "incomplete.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        ingestDataService.processFile(file);
        ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
        verify(dirtyDataRepository, times(1)).saveAll(captor.capture());
        List<DirtyData> capturedData = captor.getValue();
        assertEquals(1, capturedData.size());
        assertEquals("536374", capturedData.get(0).getInvoice());
        assertEquals("82345", capturedData.get(0).getStockCode());
        assertEquals("WHITE HANGING HEART T-LIGHT HOLDER", capturedData.get(0).getDescription());
        assertEquals("6", capturedData.get(0).getQuantity());
        assertEquals("12/1/2010 8:26", capturedData.get(0).getInvoiceDate());
        assertEquals("2.55", capturedData.get(0).getPrice());
        assertEquals("", capturedData.get(0).getCustomerID());
        assertEquals("", capturedData.get(0).getCountry());
    }
}

@WebMvcTest(IngestDataController.class)
class IngestDataControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private IngestDataService ingestDataService;

    //test controllers through mock http requests
    @Test
    void testUploadFile() throws Exception{
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", "Invoice,StockCode,Description,Quantity,InvoiceDate,UnitPrice,CustomerID,Country\n536374,82345,WHITE HANGING HEART T-LIGHT HOLDER,6,12/1/2010 8:26,2.55,17850.0,United Kingdom".getBytes());
        
        doNothing().when(ingestDataService).processFile(any(org.springframework.web.multipart.MultipartFile.class));
        mockMvc.perform(multipart("/api/ingest/upload").file(file))
                .andExpect(status().isOk());
        verify(ingestDataService, times(1)).processFile(any(org.springframework.web.multipart.MultipartFile.class));
    }
}