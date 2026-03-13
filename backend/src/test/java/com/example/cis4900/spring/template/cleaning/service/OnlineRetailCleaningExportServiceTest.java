package com.example.cis4900.spring.template.cleaning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.cis4900.spring.template.cleaning.dto.CleanedRetailExportRow;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class OnlineRetailCleaningExportServiceTest {

    @Test
    void getExportRows_delegatesToJdbcTemplate() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        OnlineRetailCleaningExportService service = new OnlineRetailCleaningExportService(
            jdbcTemplate
        );

        List<CleanedRetailExportRow> expectedRows = List.of(
            new CleanedRetailExportRow(
                "INV-1",
                "85123A",
                "Item",
                2,
                LocalDateTime.of(2020, 1, 1, 8, 30),
                new BigDecimal("9.99"),
                12345,
                "United Kingdom"
            )
        );

        Mockito.when(jdbcTemplate.query(Mockito.anyString(), Mockito.<RowMapper<CleanedRetailExportRow>>any()))
            .thenReturn(expectedRows);

        List<CleanedRetailExportRow> actualRows = service.getExportRows();

        assertEquals(expectedRows, actualRows);
        Mockito.verify(jdbcTemplate).query(
            Mockito.contains("FROM cleaned_online_retail_data"),
            Mockito.<RowMapper<CleanedRetailExportRow>>any()
        );
    }

    @Test
    void buildWorkbook_writesExpectedSheetHeadersAndRowValues() throws Exception {
        OnlineRetailCleaningExportService service = new OnlineRetailCleaningExportService(
            Mockito.mock(JdbcTemplate.class)
        );

        List<CleanedRetailExportRow> rows = List.of(
            new CleanedRetailExportRow(
                "536365",
                "85123A",
                "WHITE HANGING HEART T-LIGHT HOLDER",
                6,
                LocalDateTime.of(2010, 12, 1, 8, 26),
                new BigDecimal("2.55"),
                17850,
                "United Kingdom"
            )
        );

        byte[] workbookBytes = service.buildWorkbook(rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertEquals(1, workbook.getNumberOfSheets());

            var sheet = workbook.getSheet("cleaned_data");
            assertNotNull(sheet);

            var headerRow = sheet.getRow(0);
            assertEquals("Invoice", headerRow.getCell(0).getStringCellValue());
            assertEquals("StockCode", headerRow.getCell(1).getStringCellValue());
            assertEquals("Description", headerRow.getCell(2).getStringCellValue());
            assertEquals("Quantity", headerRow.getCell(3).getStringCellValue());
            assertEquals("InvoiceDate", headerRow.getCell(4).getStringCellValue());
            assertEquals("UnitPrice", headerRow.getCell(5).getStringCellValue());
            assertEquals("CustomerID", headerRow.getCell(6).getStringCellValue());
            assertEquals("Country", headerRow.getCell(7).getStringCellValue());

            var dataRow = sheet.getRow(1);
            assertEquals("536365", dataRow.getCell(0).getStringCellValue());
            assertEquals("85123A", dataRow.getCell(1).getStringCellValue());
            assertEquals(
                "WHITE HANGING HEART T-LIGHT HOLDER",
                dataRow.getCell(2).getStringCellValue()
            );
            assertEquals(6.0, dataRow.getCell(3).getNumericCellValue(), 0.001);
            assertEquals("2010-12-01 08:26:00", dataRow.getCell(4).getStringCellValue());
            assertEquals(2.55, dataRow.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(17850.0, dataRow.getCell(6).getNumericCellValue(), 0.001);
            assertEquals("United Kingdom", dataRow.getCell(7).getStringCellValue());
        }
    }

    @Test
    void buildWorkbook_writesBlankCellsForNullableValues() throws Exception {
        OnlineRetailCleaningExportService service = new OnlineRetailCleaningExportService(
            Mockito.mock(JdbcTemplate.class)
        );

        List<CleanedRetailExportRow> rows = List.of(
            new CleanedRetailExportRow(
                "536366",
                "22633",
                null,
                6,
                LocalDateTime.of(2010, 12, 1, 8, 28),
                new BigDecimal("1.85"),
                null,
                null
            )
        );

        byte[] workbookBytes = service.buildWorkbook(rows);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("cleaned_data");
            assertNotNull(sheet);

            var dataRow = sheet.getRow(1);
            assertEquals("", dataRow.getCell(2).getStringCellValue());
            assertEquals("", dataRow.getCell(6).getStringCellValue());
            assertEquals("", dataRow.getCell(7).getStringCellValue());
        }
    }
}