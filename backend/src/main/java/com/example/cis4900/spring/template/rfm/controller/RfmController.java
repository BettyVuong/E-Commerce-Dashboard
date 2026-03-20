package com.example.cis4900.spring.template.rfm.controller;

import com.example.cis4900.spring.template.rfm.model.HistogramResponse;
import com.example.cis4900.spring.template.rfm.model.RfmMetric;
import com.example.cis4900.spring.template.rfm.service.RfmService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/rfm")
@CrossOrigin(origins = "*")
public class RfmController {

    private final RfmService rfmService;

    public RfmController(RfmService rfmService) {
        this.rfmService = rfmService;
    }

    @GetMapping("/scatter-plot")
    public List<RfmMetric> getScatterPlotData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String country) {
        
        return rfmService.getRfmData(startDate, endDate, country);
    }

    @GetMapping("/histograms")
    public HistogramResponse getHistogramData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String country) {

        return rfmService.getHistogramData(startDate, endDate, country);
    }
}
