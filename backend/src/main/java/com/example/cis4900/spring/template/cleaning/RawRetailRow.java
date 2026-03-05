package com.example.cis4900.spring.template.cleaning;

import java.util.LinkedHashMap;
import java.util.Map;

record RawRetailRow(
    int id,
    String invoice,
    String stockCode,
    String description,
    String quantity,
    String invoiceDate,
    String price,
    String customerId,
    String country
) {

    Map<String, Object> asMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id);
        values.put("Invoice", invoice);
        values.put("StockCode", stockCode);
        values.put("Description", description);
        values.put("Quantity", quantity);
        values.put("InvoiceDate", invoiceDate);
        values.put("Price", price);
        values.put("CustomerID", customerId);
        values.put("Country", country);
        return values;
    }
}
