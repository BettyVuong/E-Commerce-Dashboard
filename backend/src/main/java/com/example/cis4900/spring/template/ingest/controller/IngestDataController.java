package com.example.cis4900.spring.template.ingest.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import com.example.cis4900.spring.template.ingest.service.IngestDataService;

@RestController
@RequestMapping("/api/ingest")
public class IngestDataController {
    private final IngestDataService ingestDataService;

    public IngestDataController(IngestDataService ingestDataService) {
        this.ingestDataService = ingestDataService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        ingestDataService.processFile(file);
        return ResponseEntity.ok("File uploaded successfully");
    }

}
