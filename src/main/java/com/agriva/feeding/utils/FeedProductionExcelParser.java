package com.agriva.feeding.utils;

import com.agriva.feeding.model.dto.request.ProductionSubmissionPayload;
import com.agriva.shared.exception.ExcelProcessingException;
import com.agriva.shared.utils.ExcelCellReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Component
@Slf4j
public class FeedProductionExcelParser {

    private static final int HEADER_ROW = 0;

    public List<ParsedRow> parse(InputStream in) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(HEADER_ROW);
            if (header == null) throw new ExcelProcessingException("Excel sheet is empty");

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell c : header) {
                String h = fmt.formatCellValue(c).trim().toLowerCase();
                if (!h.isEmpty()) colMap.put(h, c.getColumnIndex());
            }
            if (!colMap.containsKey("formulation_name")
                    || !colMap.containsKey("produced_kg")
                    || !colMap.containsKey("production_date")) {
                throw new ExcelProcessingException(
                        "Required headers: formulation_name, produced_kg, production_date. Found: "
                                + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, header.getLastCellNum())) continue;
                int rowNum = r + 1;
                try {
                    String fname = ExcelCellReader.readString(getCell(row, colMap, "formulation_name"));
                    if (fname == null || fname.isBlank())
                        throw new ExcelProcessingException("formulation_name is required");
                    BigDecimal kg = ExcelCellReader.readBigDecimal(
                            getCell(row, colMap, "produced_kg"), "produced_kg");
                    LocalDate date = ExcelCellReader.readDate(
                            getCell(row, colMap, "production_date"), "production_date");
                    String notes = ExcelCellReader.readString(getCell(row, colMap, "notes"));
                    rows.add(new ParsedRow(rowNum, new ProductionSubmissionPayload(
                            fname.trim(), kg, date, notes), null));
                } catch (Exception ex) {
                    log.warn("Feed-production Excel row {} failed: {}", rowNum, ex.getMessage());
                    rows.add(new ParsedRow(rowNum, null, ex.getMessage()));
                }
            }
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException("Failed to parse Excel: " + e.getMessage(), e);
        }
        return rows;
    }

    private static Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    public record ParsedRow(int rowNumber, ProductionSubmissionPayload payload, String error) {
        public boolean isError() { return error != null; }
    }
}