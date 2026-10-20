package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.income.IncomeRequest;
import com.agriva.model.entity.Currency;
import com.agriva.model.entity.IncomeCategory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class IncomeExcelParser {

    private static final int HEADER_ROW = 0;

    public List<ParsedRow> parse(InputStream in) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) throw new ExcelProcessingException("Excel sheet is empty");

            // Build header → column-index map (case-insensitive)
            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                if (!header.isEmpty()) colMap.put(header, cell.getColumnIndex());
            }

            if (!colMap.containsKey("reason")
                    || !colMap.containsKey("incomedate")
                    || !colMap.containsKey("amountreceived")) {
                throw new ExcelProcessingException(
                        "Missing required headers. Required: reason, incomeDate, amountReceived. "
                                + "Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, headerRow.getLastCellNum())) continue;

                int rowNumber = r + 1;
                try {
                    IncomeRequest request = mapRow(row, colMap);
                    rows.add(new ParsedRow(rowNumber, request, null));
                } catch (Exception ex) {
                    log.warn("Income Excel row {} failed to parse: {}", rowNumber, ex.getMessage());
                    rows.add(new ParsedRow(rowNumber, null, ex.getMessage()));
                }
            }
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private IncomeRequest mapRow(Row row, Map<String, Integer> colMap) {
        IncomeRequest req = new IncomeRequest();

        req.setReason(ExcelCellReader.readString(getCell(row, colMap, "reason")));
        req.setIncomeDate(ExcelCellReader.readDate(getCell(row, colMap, "incomedate"), "Income date"));

        String qtyStr = ExcelCellReader.readString(getCell(row, colMap, "quantity"));
        req.setQuantity(qtyStr != null && !qtyStr.isBlank() ? Integer.parseInt(qtyStr) : 1);

        req.setAmountReceived(ExcelCellReader.readBigDecimal(getCell(row, colMap, "amountreceived"), "Amount received"));
        req.setExpectedAmount(ExcelCellReader.readBigDecimal(getCell(row, colMap, "expectedamount"), "Expected amount"));

        // Optional fields (will return null if column doesn't exist)
        req.setReceipt(ExcelCellReader.readString(getCell(row, colMap, "receipt")));

        String currencyStr = ExcelCellReader.readString(getCell(row, colMap, "currency"));
        req.setCurrency(parseCurrency(currencyStr));

        String categoryStr = ExcelCellReader.readString(getCell(row, colMap, "category"));
        req.setCategory(parseCategory(categoryStr));

        return req;
    }

    /**
     * Safely retrieves a cell. Returns null if the column is missing entirely,
     * preventing the "Cell index must be >= 0" error.
     */
    private Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        if (idx == null || idx < 0) return null;
        return row.getCell(idx);
    }

    private Currency parseCurrency(String s) {
        if (s == null || s.isBlank()) return Currency.XAF;
        try { return Currency.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { return Currency.XAF; }
    }

    private IncomeCategory parseCategory(String s) {
        if (s == null || s.isBlank()) return IncomeCategory.MISCELLANEOUS;
        try { return IncomeCategory.valueOf(s.trim().toUpperCase().replace(" ", "_")); }
        catch (Exception e) { return IncomeCategory.MISCELLANEOUS; }
    }

    public record ParsedRow(int rowNumber, IncomeRequest request, String error) {
        public boolean isError() { return error != null; }
    }
}