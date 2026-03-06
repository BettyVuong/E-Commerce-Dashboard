package com.example.cis4900.spring.template.ingest.service;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.cis4900.spring.template.ingest.model.DirtyData;
import com.example.cis4900.spring.template.ingest.repository.DirtyDataRepository;
import com.opencsv.CSVReader;

import jakarta.transaction.Transactional;


@Service
public class IngestDataService {
    //auto injects the dirtyDataRepository into the service
    private final DirtyDataRepository dirtyDataRepository;

    public IngestDataService(DirtyDataRepository dirtyDataRepository) {
        this.dirtyDataRepository = dirtyDataRepository;
    }

    @Transactional
    public void processFile(MultipartFile file) {
        // file processing, e.g. read CSV and save to database
        String fileName = file.getOriginalFilename();
        if (fileName.toLowerCase().endsWith(".csv")) {
            // process CSV file
            List<DirtyData> dirtyDataList = parseCsv(file);
            dirtyDataRepository.saveAll(dirtyDataList);
        } else if (fileName.toLowerCase().endsWith(".xlsx")) {
            // process Excel file
            List<DirtyData> dirtyDataList = parseXlsx(file);
            dirtyDataRepository.saveAll(dirtyDataList);
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + fileName);
        }
    }

    private List<DirtyData> parseCsv(MultipartFile file) {
        List<DirtyData> dirtyDataList = new ArrayList<>();
        // Using OpenCSV to read the CSV file
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            String[] line;
            boolean isFirstLine = true;
            // Loop through each line in the CSV file and create DirtyData objects
            while ((line = reader.readNext()) != null) {
                //ignore header line since its not relevant data just column names
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                // Assuming CSV columns: id, name, value, timestamp
                DirtyData dirtyData = new DirtyData(
                    line[0], // invoice
                    line[1], // stockCode
                    line[2], // description
                    line[3], // quantity
                    line[4], // invoiceDate
                    line[5], // unitPrice
                    line[6], // customerID
                    line[7]  // country
                );
                // Add the created DirtyData object to the list
                dirtyDataList.add(dirtyData);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file", e);
        }
        return dirtyDataList;
    }

    private List<DirtyData> parseXlsx(MultipartFile file) {
        List<DirtyData> dirtyDataList = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean isFirstRow = true;
            for (Row row : sheet) {
                if (isFirstRow) {
                    isFirstRow = false;
                    continue;
                }
                DirtyData dirtyData = new DirtyData(
                    row.getCell(0).getStringCellValue(), // invoice
                    row.getCell(1).getStringCellValue(), // stockCode
                    row.getCell(2).getStringCellValue(), // description
                    row.getCell(3).getStringCellValue(), // quantity
                    row.getCell(4).getStringCellValue(), // invoiceDate
                    row.getCell(5).getStringCellValue(), // unitPrice
                    row.getCell(6).getStringCellValue(), // customerID
                    row.getCell(7).getStringCellValue()  // country
                );
                dirtyDataList.add(dirtyData);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Excel file", e);
        }
        return dirtyDataList;
    }
}
