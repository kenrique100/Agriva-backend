package com.agriva.feeding.utils;

import com.agriva.feeding.model.dto.request.FeedingSubmissionPayload;
import com.agriva.feeding.port.ConsumerType;
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
public class FeedFeedingExcelParser {

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
            if (!colMap.containsKey("consumer_type")
                    || !colMap.containsKey("consumer_name")
                    || !colMap.containsKey("quantity_kg")
                    || !colMap.containsKey("fed_at")) {
                throw new ExcelProcessingException(
                        "Required headers: consumer_type, consumer_name, quantity_kg, fed_at. Found: "
                                + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, header.getLastCellNum())) continue;
                int rowNum = r + 1;
                try {
                    String typeStr = ExcelCellReader.readString(getCell(row, colMap, "consumer_type"));
                    ConsumerType type;
                    try {
                        type = ConsumerType.valueOf(typeStr.trim().toUpperCase());
                    } catch (Exception e) {
                        throw new ExcelProcessingException("Invalid consumer_type: " + typeStr);
                    }

                    String consumerName = ExcelCellReader.readString(
                            getCell(row, colMap, "consumer_name"));
                    if (consumerName == null || consumerName.isBlank())
                        throw new ExcelProcessingException("consumer_name is required");

                    BigDecimal qty = ExcelCellReader.readBigDecimal(
                            getCell(row, colMap, "quantity_kg"), "quantity_kg");
                    LocalDate fedAt = ExcelCellReader.readDate(
                            getCell(row, colMap, "fed_at"), "fed_at");
                    String notes = ExcelCellReader.readString(getCell(row, colMap, "notes"));

                    rows.add(new ParsedRow(rowNum, new FeedingSubmissionPayload(
                            type, consumerName.trim(), qty, fedAt, notes), null));
                } catch (Exception ex) {
                    log.warn("Feed-feeding Excel row {} failed: {}", rowNum, ex.getMessage());
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

    public record ParsedRow(int rowNumber, FeedingSubmissionPayload payload, String error) {
        public boolean isError() { return error != null; }
    }
}