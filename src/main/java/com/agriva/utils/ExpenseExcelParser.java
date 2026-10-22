package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.expense.ExpenseRequest;
import com.agriva.model.entity.enums.Currency;
import com.agriva.model.entity.enums.ExpenseCategory;
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
public class ExpenseExcelParser {

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

            if (!colMap.containsKey("reason")
                    || !colMap.containsKey("expensedate")
                    || !colMap.containsKey("amountpaid")) {
                throw new ExcelProcessingException(
                        "Missing required headers. Required: reason, expenseDate, amountPaid. "
                                + "Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, headerRow.getLastCellNum())) continue;

                int rowNumber = r + 1;
                try {
                    ExpenseRequest request = mapRow(row, colMap);
                    rows.add(new ParsedRow(rowNumber, request, null));
                } catch (Exception ex) {
                    log.warn("Expense Excel row {} failed to parse: {}", rowNumber, ex.getMessage());
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

    private ExpenseRequest mapRow(Row row, Map<String, Integer> colMap) {
        ExpenseRequest req = new ExpenseRequest();

        req.setReason(ExcelCellReader.readString(getCell(row, colMap, "reason")));
        req.setExpenseDate(ExcelCellReader.readDate(getCell(row, colMap, "expensedate"), "Expense date"));

        String qtyStr = ExcelCellReader.readString(getCell(row, colMap, "qtypurchased"));
        req.setQtyPurchased(qtyStr != null && !qtyStr.isBlank() ? Integer.parseInt(qtyStr) : 1);

        req.setAmountPaid(ExcelCellReader.readBigDecimal(getCell(row, colMap, "amountpaid"), "Amount paid"));
        req.setExpectedAmount(ExcelCellReader.readBigDecimal(getCell(row, colMap, "expectedamount"), "Expected amount"));

        req.setReceipt(ExcelCellReader.readString(getCell(row, colMap, "receipt")));

        String currencyStr = ExcelCellReader.readString(getCell(row, colMap, "currency"));
        req.setCurrency(parseCurrency(currencyStr));

        String categoryStr = ExcelCellReader.readString(getCell(row, colMap, "category"));
        req.setCategory(parseCategory(categoryStr));

        return req;
    }

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

    private ExpenseCategory parseCategory(String s) {
        if (s == null || s.isBlank()) return ExpenseCategory.MISCELLANEOUS;
        try { return ExpenseCategory.valueOf(s.trim().toUpperCase().replace(" ", "_")); }
        catch (Exception e) { return ExpenseCategory.MISCELLANEOUS; }
    }

    public record ParsedRow(int rowNumber, ExpenseRequest request, String error) {
        public boolean isError() { return error != null; }
    }
}