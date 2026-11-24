package com.agriva.shared.utils;

import com.agriva.shared.exception.ExcelProcessingException;
import com.agriva.shared.models.enums.Currency;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public abstract class AbstractExcelBatchParser<R> {

    protected static final int HEADER_ROW = 0;

    /** Payload type this parser produces (used only for logging). */
    protected abstract Class<R> requestType();

    /** Headers (lowercased, whitespace-trimmed) that must be present. */
    protected abstract List<String> requiredHeaders();

    /** Map a single data row into the domain payload. */
    protected abstract R mapRow(Row row, Map<String, Integer> colMap);

    public List<BatchParsedRow<R>> parse(InputStream in) {
        List<BatchParsedRow<R>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) {
                throw new ExcelProcessingException("Excel sheet is empty");
            }

            Map<String, Integer> colMap = buildColMap(headerRow, formatter);
            validateRequiredHeaders(colMap);

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, headerRow.getLastCellNum())) {
                    continue;
                }
                int rowNumber = r + 1;
                try {
                    R request = mapRow(row, colMap);
                    rows.add(new BatchParsedRow<>(rowNumber, request, null));
                } catch (Exception ex) {
                    log.warn("{} Excel row {} failed to parse: {}",
                            requestType().getSimpleName(), rowNumber, ex.getMessage());
                    rows.add(new BatchParsedRow<>(rowNumber, null, ex.getMessage()));
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

    protected Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        if (idx == null || idx < 0) return null;
        return row.getCell(idx);
    }

    protected Currency parseCurrency(String s) {
        if (s == null || s.isBlank()) return Currency.XAF;
        try { return Currency.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return Currency.XAF; }
    }

    protected <E extends Enum<E>> E parseEnum(String s, Class<E> type, E defaultValue) {
        if (s == null || s.isBlank()) return defaultValue;
        try {
            return Enum.valueOf(type, s.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private Map<String, Integer> buildColMap(Row headerRow, DataFormatter formatter) {
        Map<String, Integer> colMap = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = formatter.formatCellValue(cell).trim().toLowerCase();
            if (!header.isEmpty()) {
                colMap.put(header, cell.getColumnIndex());
            }
        }
        return colMap;
    }

    private void validateRequiredHeaders(Map<String, Integer> colMap) {
        List<String> missing = requiredHeaders().stream()
                .filter(h -> !colMap.containsKey(h))
                .toList();
        if (!missing.isEmpty()) {
            throw new ExcelProcessingException(
                    "Missing required headers: " + missing
                            + ". Found: " + colMap.keySet());
        }
    }
}