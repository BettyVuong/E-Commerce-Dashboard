package com.example.cis4900.spring.template.ingest.service;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.DataFormatter;
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
    private final DataFormatter dataFormatter = new DataFormatter();
    
    public IngestDataService(DirtyDataRepository dirtyDataRepository) {
        this.dirtyDataRepository = dirtyDataRepository;
    }

    @Transactional
    public void processFile(MultipartFile file) {
        // file processing, e.g. read CSV and save to database
        String fileName = file.getOriginalFilename();
        //check if correct file type has no information
        if (fileName == null || file.isEmpty() || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        //check if file type is supported (csv or xlsx)
        if (fileName.toLowerCase().endsWith(".csv")) {
            // process CSV file
            List<DirtyData> dirtyDataList = parseCsv(file);
            dirtyDataRepository.saveAll(dirtyDataList);
        } else if (fileName.endsWith(".xlsx")) {
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
                    getValue(line, 0), // invoice
                    getValue(line, 1), // stockCode
                    getValue(line, 2), // description
                    getValue(line, 3), // quantity
                    getValue(line, 4), // invoiceDate
                    getValue(line, 5), // unitPrice
                    getValue(line, 6), // customerID
                    getValue(line, 7)  // country
                );
                // Add the created DirtyData object to the list
                dirtyDataList.add(dirtyData);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV file", e);
        }

        //if parse is empty
        if (dirtyDataList.isEmpty()) {
            throw new RuntimeException("CSV file is empty or only contains headers");
        }
        return dirtyDataList;
    }

    //helper method to get all data from the csv file and handle missing values by returning empty string
    private String getValue(String [] line, int index) {
        if (line.length > index && line[index] != null && !line[index].isEmpty()) {
            return line[index];
        } else {
            return ""; //default to empty for null value
        }
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
                    getCellValue(row, 0), // invoice
                    getCellValue(row, 1), // stockCode
                    getCellValue(row, 2), // description
                    getCellValue(row, 3), // quantity
                    getCellValue(row, 4), // invoiceDate
                    getCellValue(row, 5), // unitPrice
                    getCellValue(row, 6), // customerID
                    getCellValue(row, 7)  // country
                );
                dirtyDataList.add(dirtyData);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Excel file", e);
        }
        
        //if parse is empty
        if (dirtyDataList.isEmpty()) {
            throw new RuntimeException("Excel file is empty or only contains headers");
        }
        return dirtyDataList;
    }

    private String getCellValue(Row row, int index) {
        if (row.getCell(index) != null) {
            return dataFormatter.formatCellValue(row.getCell(index));
        } else {
            return ""; //default to empty for null value
        }
    }
}
