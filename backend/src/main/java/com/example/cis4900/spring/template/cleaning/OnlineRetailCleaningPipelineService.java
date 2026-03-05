package com.example.cis4900.spring.template.cleaning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnlineRetailCleaningPipelineService {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final OnlineRetailRowCleaner rowCleaner;
    private final ObjectMapper objectMapper;

    OnlineRetailCleaningPipelineService(
        JdbcTemplate jdbcTemplate,
        OnlineRetailRowCleaner rowCleaner,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.rowCleaner = rowCleaner;
        this.objectMapper = objectMapper;
    }

    @Transactional
    CleaningRunSummary runCleaning(Integer requestedBatchSize) {
        int batchSize = requestedBatchSize == null || requestedBatchSize <= 0
            ? DEFAULT_BATCH_SIZE
            : requestedBatchSize;

        long totalRowsProcessed = 0L;
        long rowsInsertedIntoCleaned = 0L;
        long rowsFlaggedRejected = 0L;
        long rowsFlaggedAutoCleaned = 0L;
        long returnsDetected = 0L;

        int lastSeenId = 0;
        List<RawRetailRow> batch;

        do {
            batch = loadBatch(lastSeenId, batchSize);
            for (RawRetailRow rawRow : batch) {
                totalRowsProcessed++;
                lastSeenId = rawRow.id();

                try {
                    CleaningDecision decision = rowCleaner.cleanRow(rawRow);

                    if (decision.shouldInsertCleanedRecord()) {
                        insertCleanedRecord(decision.cleanedRecord());
                        rowsInsertedIntoCleaned++;
                    }

                    if (decision.shouldInsertReviewRecord()) {
                        insertManualReview(rawRow, decision);
                        if (decision.reviewStatus() == CleaningReviewStatus.REJECTED) {
                            rowsFlaggedRejected++;
                        }
                        if (decision.reviewStatus() == CleaningReviewStatus.AUTO_CLEANED) {
                            rowsFlaggedAutoCleaned++;
                        }
                    }

                    if (decision.cleanedRecord() != null && decision.cleanedRecord().isReturn()) {
                        returnsDetected++;
                    }
                } catch (RuntimeException exception) {
                    rowsFlaggedRejected++;
                    insertProcessingFailure(rawRow, exception);
                }
            }
        } while (!batch.isEmpty());

        return new CleaningRunSummary(
            totalRowsProcessed,
            rowsInsertedIntoCleaned,
            rowsFlaggedRejected,
            rowsFlaggedAutoCleaned,
            returnsDetected
        );
    }

    private List<RawRetailRow> loadBatch(int lastSeenId, int batchSize) {
        return jdbcTemplate.query(
            """
            SELECT id, Invoice, StockCode, Description, Quantity, InvoiceDate, Price, CustomerID, Country
            FROM dirty_data
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """,
            rawRetailRowMapper(),
            lastSeenId,
            batchSize
        );
    }

    private RowMapper<RawRetailRow> rawRetailRowMapper() {
        return (resultSet, rowNum) -> mapRawRow(resultSet);
    }

    private RawRetailRow mapRawRow(ResultSet resultSet) throws SQLException {
        return new RawRetailRow(
            resultSet.getInt("id"),
            resultSet.getString("Invoice"),
            resultSet.getString("StockCode"),
            resultSet.getString("Description"),
            resultSet.getString("Quantity"),
            resultSet.getString("InvoiceDate"),
            resultSet.getString("Price"),
            resultSet.getString("CustomerID"),
            resultSet.getString("Country")
        );
    }

    private void insertCleanedRecord(CleanedRetailRecord record) {
        jdbcTemplate.update(
            """
            INSERT INTO cleaned_online_retail_data
            (raw_data_id, Invoice, StockCode, Description, Quantity, InvoiceDate, Price, CustomerID, Country, is_return)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                Invoice = VALUES(Invoice),
                StockCode = VALUES(StockCode),
                Description = VALUES(Description),
                Quantity = VALUES(Quantity),
                InvoiceDate = VALUES(InvoiceDate),
                Price = VALUES(Price),
                CustomerID = VALUES(CustomerID),
                Country = VALUES(Country),
                is_return = VALUES(is_return)
            """,
            record.rawDataId(),
            record.invoice(),
            record.stockCode(),
            record.description(),
            record.quantity(),
            Timestamp.valueOf(record.invoiceDate()),
            record.price(),
            record.customerId(),
            record.country(),
            record.isReturn()
        );
    }

    private void insertManualReview(RawRetailRow rawRow, CleaningDecision decision) {
        jdbcTemplate.update(
            """
            INSERT INTO online_retail_manual_review
            (raw_data_id, review_status, reason, validation_errors, raw_values, cleaned_values)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                review_status = VALUES(review_status),
                reason = VALUES(reason),
                validation_errors = VALUES(validation_errors),
                raw_values = VALUES(raw_values),
                cleaned_values = VALUES(cleaned_values)
            """,
            rawRow.id(),
            decision.reviewStatus().name(),
            String.join("; ", decision.reviewReasons()),
            decision.validationErrors().isEmpty() ? null : String.join("; ", decision.validationErrors()),
            safeJson(rawRow.asMap()),
            decision.cleanedRecord() == null ? null : safeJson(decision.cleanedRecord().asMap())
        );
    }

    private void insertProcessingFailure(RawRetailRow rawRow, RuntimeException exception) {
        jdbcTemplate.update(
            """
            INSERT INTO online_retail_manual_review
            (raw_data_id, review_status, reason, validation_errors, raw_values, cleaned_values)
            VALUES (?, 'REJECTED', ?, ?, ?, NULL)
            ON DUPLICATE KEY UPDATE
                review_status = VALUES(review_status),
                reason = VALUES(reason),
                validation_errors = VALUES(validation_errors),
                raw_values = VALUES(raw_values),
                cleaned_values = VALUES(cleaned_values)
            """,
            rawRow.id(),
            "Unhandled exception during cleaning",
            exception.getClass().getSimpleName() + ": " + exception.getMessage(),
            safeJson(rawRow.asMap())
        );
    }

    private String safeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":\"" + exception.getMessage() + "\"}";
        }
    }
}
