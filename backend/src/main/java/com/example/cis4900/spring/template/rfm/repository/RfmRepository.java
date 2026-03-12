package com.example.cis4900.spring.template.rfm.repository;

import com.example.cis4900.spring.template.rfm.model.RfmMetric;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.time.LocalDateTime;

@org.springframework.stereotype.Repository
public interface RfmRepository extends Repository<Object, Long> {
    /**
    * Native Query for RFM analysis.
    */
    @Query(value = """
        SELECT 
            CustomerID AS customerID,
            DATEDIFF(CURRENT_DATE, MAX(InvoiceDate)) AS recency,
            COUNT(DISTINCT Invoice) AS orderID,
            SUM(Quantity * Price) AS totalAmount,
            Country AS country,
            -- Normalization Logic: SQRT handles the variance so bubbles look good
            SQRT(SUM(Quantity * Price)) AS bubbleSize 
        FROM cleaned_online_retail_data
        WHERE CustomerID IS NOT NULL 
            AND is_return = FALSE                           
            AND InvoiceDate BETWEEN :startDate AND :endDate  
            AND (:country IS NULL OR Country = :country) 
        GROUP BY CustomerID, Country
        """, nativeQuery = true)
        List<RfmMetric> findRfmStats(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("country") String country
    );
}
