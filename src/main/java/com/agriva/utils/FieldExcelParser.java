package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.crop.FieldRequest;
import com.agriva.model.entity.enums.SoilType;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FieldExcelParser {

    private static final int HEADER_ROW = 0;

    public List<ParsedRow> parse(InputStream in) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) throw new ExcelProcessingException("Excel sheet is empty");

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                if (!header.isEmpty()) colMap.put(header, cell.getColumnIndex());
            }

            if (!colMap.containsKey("name")) {
                throw new ExcelProcessingException(
                        "Missing required header: name. Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, headerRow.getLastCellNum()))
                    continue;

                int rowNumber = r + 1;
                try {
                    FieldRequest req = new FieldRequest();
                    req.setName(ExcelCellReader.readString(getCell(row, colMap, "name")));

                    String sizeStr = ExcelCellReader.readString(
                            getCell(row, colMap, "size_hectares"));
                    if (sizeStr != null && !sizeStr.isBlank()) {
                        req.setSizeHectares(new BigDecimal(sizeStr));
                    }

                    // Soil type is now an enum — accept both enum names and display names.
                    String soilStr = ExcelCellReader.readString(
                            getCell(row, colMap, "soil_type"));
                    req.setSoilType(SoilType.fromString(soilStr));

                    rows.add(new ParsedRow(rowNumber, req, null));
                } catch (Exception ex) {
                    log.warn("Field Excel row {} failed: {}", rowNumber, ex.getMessage());
                    rows.add(new ParsedRow(rowNumber, null, ex.getMessage()));
                }
            }
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException(
                    "Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    public record ParsedRow(int rowNumber, FieldRequest request, String error) {
        public boolean isError() { return error != null; }
    }
}