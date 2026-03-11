/**
* RFM Raw aggregation
* This query extracts base metrics srequired for RFM scatter plot
* Recency: latest InvoiceData per customer
* Frequency: count of unique invoice numbers/customer
* Monetary: total spend (quanity * price) per customer
*/

SELECT 
    CustomerID,
    MAX(InvoiceDate) AS LastPurchaseDate, -- raw recency
    COUNT(DISTINCT Invoice) AS RawFrequency,-- raw frequency
    SUM(Quantity * Price) AS RawMonetary, -- raw monetary
    Country -- for filtering 
FROM cleaned_online_retail_data
WHERE CustomerID IS NOT NULL 
-- allow backend to inject the filters from the UI
  AND is_return = FALSE                           
  AND InvoiceDate BETWEEN :startDate AND :endDate  
  AND (:country IS NULL OR Country = :country) 
GROUP BY CustomerID, Country;