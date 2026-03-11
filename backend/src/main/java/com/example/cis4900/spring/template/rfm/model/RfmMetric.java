package com.example.cis4900.spring.template.rfm.model;

import java.math.BigDecimal;

/**
 * interface based projection to hold results of RFM aggregation query
 */
public interface RfmMetric {
    String getCustomerID();
    Integer getRecency(); // x-axis: number of days
    Long getOrderID(); // y-axis frequency           
    BigDecimal getTotalAmount();  // raw monetary value for hover
    String getCountry();
    Double getBubbleSize(); // normalize scale
}
