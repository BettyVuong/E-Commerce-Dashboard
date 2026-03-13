package com.example.cis4900.spring.template.cleaning.service;

import com.example.cis4900.spring.template.cleaning.dto.CleanedRetailExportRow;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OnlineRetailCleaningExportService {

    private static final String SHEET_NAME = "cleaned_data";
    private static final int EXPORT_BATCH_SIZE = 1000;
    private static final int ROW_WINDOW_SIZE = 100;
    private static final DateTimeFormatter EXPORT_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Stores DB helper the service will use
    private final JdbcTemplate jdbcTemplate;
    // Spring supplies JdbcTemplate

    public OnlineRetailCleaningExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean hasExportRows() {
        // Check for one row before opening the stream
        return !jdbcTemplate.queryForList(
            "SELECT 1 FROM cleaned_online_retail_data LIMIT 1"
        ).isEmpty();
    }

    // Turn rows into a real excel file
    public void writeWorkbook(OutputStream outputStream) {
        // Keep only a small workbook window in memory
        SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_WINDOW_SIZE);
        workbook.setCompressTempFiles(true);

        try {
            // Build the sheet once and append rows as batches arrive
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            writeHeaderRow(sheet);

            // Read the export in small batches
            long lastSeenRawDataId = 0L;
            int rowIndex = 1;

            while (true) {
                List<Map<String, Object>> batch = loadExportBatch(lastSeenRawDataId);

                if (batch.isEmpty()) {
                    break;
                }

                for (Map<String, Object> batchRow : batch) {
                    // Advance the cursor after each row so the next query picks up where this one ended
                    writeDataRow(sheet, rowIndex++, toExportRow(batchRow));
                    lastSeenRawDataId = readRequiredLong(batchRow, "raw_data_id");
                }
            }

            // Send workbook bytes directly to the HTTP response stream
            workbook.write(outputStream);
            outputStream.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build cleaned data workbook", exception);
        } finally {
            workbook.dispose();
            try {
                workbook.close();
            } catch (IOException ignored) {
                // Best effort cleanup for workbook resources
            }
        }
    }

    private List<Map<String, Object>> loadExportBatch(long lastSeenRawDataId) {
        // Page forward by raw data id to avoid loading the full table at once
        return jdbcTemplate.queryForList(
            """
            SELECT raw_data_id, Invoice, StockCode, Description, Quantity, InvoiceDate, Price,
            CustomerID, Country
            FROM cleaned_online_retail_data
            WHERE raw_data_id > ?
            ORDER BY raw_data_id ASC
            LIMIT ?
            """,
            lastSeenRawDataId,
            EXPORT_BATCH_SIZE
        );
    }

    private static CleanedRetailExportRow toExportRow(Map<String, Object> rowValues) {
        // Convert the raw query map into the export projection used by row writing
        Timestamp invoiceTimestamp = (Timestamp) rowValues.get("InvoiceDate");

        return new CleanedRetailExportRow(
            (String) rowValues.get("Invoice"),
            (String) rowValues.get("StockCode"),
            (String) rowValues.get("Description"),
            readRequiredInt(rowValues, "Quantity"),
            invoiceTimestamp.toLocalDateTime(),
            readRequiredNumber(rowValues, "Price"),
            readOptionalInteger(rowValues, "CustomerID"),
            (String) rowValues.get("Country")
        );
    }

    private static void writeHeaderRow(Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Invoice");
        headerRow.createCell(1).setCellValue("StockCode");
        headerRow.createCell(2).setCellValue("Description");
        headerRow.createCell(3).setCellValue("Quantity");
        headerRow.createCell(4).setCellValue("InvoiceDate");
        headerRow.createCell(5).setCellValue("UnitPrice");
        headerRow.createCell(6).setCellValue("CustomerID");
        headerRow.createCell(7).setCellValue("Country");
    }

    private static void writeDataRow(Sheet sheet, int rowIndex, CleanedRetailExportRow exportRow) {
        Row row = sheet.createRow(rowIndex);

        row.createCell(0).setCellValue(safeString(exportRow.invoice()));
        row.createCell(1).setCellValue(safeString(exportRow.stockCode()));
        row.createCell(2).setCellValue(safeString(exportRow.description()));
        row.createCell(3).setCellValue(exportRow.quantity());
        row.createCell(4).setCellValue(EXPORT_DATE_FORMAT.format(exportRow.invoiceDate()));
        row.createCell(5).setCellValue(exportRow.unitPrice().doubleValue());

        if (exportRow.customerId() != null) {
            row.createCell(6).setCellValue(exportRow.customerId());
        } else {
            row.createCell(6).setCellValue("");
        }

        row.createCell(7).setCellValue(safeString(exportRow.country()));
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static int readRequiredInt(Map<String, Object> rowValues, String key) {
        return ((Number) rowValues.get(key)).intValue();
    }

    private static Integer readOptionalInteger(Map<String, Object> rowValues, String key) {
        Object value = rowValues.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private static BigDecimal readRequiredNumber(Map<String, Object> rowValues, String key) {
        return (BigDecimal) rowValues.get(key);
    }

    private static long readRequiredLong(Map<String, Object> rowValues, String key) {
        return ((Number) rowValues.get(key)).longValue();
    }
}
