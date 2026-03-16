package com.example.cis4900.spring.template.rfm.service;

import com.example.cis4900.spring.template.rfm.model.RfmMetric;
import com.example.cis4900.spring.template.rfm.repository.RfmRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RfmService {

    private final RfmRepository rfmRepository;

    public RfmService(RfmRepository rfmRepository) {
        this.rfmRepository = rfmRepository;
    }

    public List<RfmMetric> getRfmData(LocalDateTime start, LocalDateTime end, String country) {
        return rfmRepository.findRfmStats(start, end, country);
    }
}
