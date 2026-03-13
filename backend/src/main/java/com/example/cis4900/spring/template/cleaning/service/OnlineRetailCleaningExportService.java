package com.example.cis4900.spring.template.cleaning.service;

import com.example.cis4900.spring.template.cleaning.dto.CleanedRetailExportRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OnlineRetailCleaningExportService {

    private static final String SHEET_NAME = "cleaned_data";
    private static final DateTimeFormatter EXPORT_DATE_FORMAT = 
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Stores DB helper the service will use
    private final JdbcTemplate jdbcTemplate;
    // Spring supplies JdbcTemplate
    public OnlineRetailCleaningExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

   
    public List<CleanedRetailExportRow> getExportRows() {
        return jdbcTemplate.query(
            """
            SELECT Invoice, StockCode, Description, Quantity, InvoiceDate, Price, CustomerID, Country
            FROM cleaned_online_retail_data
            ORDER BY raw_data_id ASC
            """,
            (resultSet, rowNum) -> new CleanedRetailExportRow(
                resultSet.getString("Invoice"),
                resultSet.getString("StockCode"),
                resultSet.getString("Description"),
                resultSet.getInt("Quantity"),
                resultSet.getTimestamp("InvoiceDate").toLocalDateTime(),
                resultSet.getBigDecimal("Price"),
                (Integer) resultSet.getObject("CustomerID"),
                resultSet.getString("Country")
            )
        );
    }

    //Turn rows into a real excel file
    public byte[] buildWorkbook(List<CleanedRetailExportRow> rows) {
        try (
            XSSFWorkbook workbook = new XSSFWorkbook(); //Create excel workbook in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream() //Create in memory byte buffer
        ) {
            Sheet sheet = workbook.createSheet(SHEET_NAME); //Add worksheet to workbook
            writeHeaderRow(sheet); //Create column name row

            //Write each data row starting at 1 (0 is header)
            for (int i = 0; i < rows.size(); i++) {
                writeDataRow(sheet, i + 1, rows.get(i));
            } 

            workbook.write(outputStream); //Turn into raw bytes
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build cleaned data workbook", exception);
        }
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
}
