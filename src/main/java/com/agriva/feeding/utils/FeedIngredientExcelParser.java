package com.agriva.feeding.utils;

import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.exception.ExcelProcessingException;
import com.agriva.shared.models.enums.Currency;
import com.agriva.shared.utils.ExcelCellReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class FeedIngredientExcelParser {

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

            if (!colMap.containsKey("name") || !colMap.containsKey("unit")) {
                throw new ExcelProcessingException(
                        "Required headers: name, unit. Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, header.getLastCellNum())) continue;
                int rowNum = r + 1;
                try {
                    String name = ExcelCellReader.readString(getCell(row, colMap, "name"));
                    if (name == null || name.isBlank())
                        throw new ExcelProcessingException("name is required");

                    String unitStr = ExcelCellReader.readString(getCell(row, colMap, "unit"));
                    IngredientUnit unit;
                    try {
                        unit = IngredientUnit.valueOf(unitStr.trim().toUpperCase());
                    } catch (Exception e) {
                        throw new ExcelProcessingException(
                                "Invalid unit: " + unitStr + " (expected KG/L/EACH)");
                    }

                    BigDecimal qty = readOptionalDecimal(row, colMap, "quantity");
                    BigDecimal cost = readOptionalDecimal(row, colMap, "cost_per_unit");
                    BigDecimal density = readOptionalDecimal(row, colMap, "density_kg_per_l");

                    String currencyStr = ExcelCellReader.readString(getCell(row, colMap, "currency"));
                    Currency currency = null;
                    if (currencyStr != null && !currencyStr.isBlank()) {
                        try {
                            currency = Currency.valueOf(currencyStr.trim().toUpperCase());
                        } catch (Exception e) {
                            throw new ExcelProcessingException("Invalid currency: " + currencyStr);
                        }
                    }

                    String supplier = ExcelCellReader.readString(getCell(row, colMap, "supplier"));

                    rows.add(new ParsedRow(rowNum, new IngredientSubmissionPayload(
                            name.trim(), unit, qty, cost, currency, supplier, density), null));
                } catch (Exception ex) {
                    log.warn("Feed-ingredient Excel row {} failed: {}", rowNum, ex.getMessage());
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

    private static BigDecimal readOptionalDecimal(Row row, Map<String, Integer> colMap, String key) {
        String s = ExcelCellReader.readString(getCell(row, colMap, key));
        return (s == null || s.isBlank()) ? null : new BigDecimal(s.replace(",", ""));
    }

    private static Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    public record ParsedRow(int rowNumber, IngredientSubmissionPayload payload, String error) {
        public boolean isError() { return error != null; }
    }
}