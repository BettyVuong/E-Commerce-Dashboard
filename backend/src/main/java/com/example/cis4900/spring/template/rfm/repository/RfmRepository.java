package com.example.cis4900.spring.template.rfm.repository;

import com.example.cis4900.spring.template.rfm.model.RfmMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface RfmRepository extends JpaRepository<com.example.cis4900.spring.template.cleaning.model.CleanedRetailRecord, Long> {
    /**
    * Native Query for RFM analysis.
    */
    @Query(value = """
        SELECT 
            CustomerID,
            DATEDIFF(CURRENT_DATE, MAX(InvoiceDate)) AS Recency,
            COUNT(DISTINCT Invoice) AS OrderID,
            SUM(Quantity * Price) AS TotalAmount,
            Country,
            -- Normalization Logic: SQRT handles the variance so bubbles look good
            SQRT(SUM(Quantity * Price)) AS BubbleSize 
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
