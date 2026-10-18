package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.income.IncomeRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class IncomeExcelParser {

    private static final int HEADER_ROW = 0;
    private static final int COL_AMOUNT = 0;
    private static final int COL_CURRENCY = 1;
    private static final int COL_SOURCE = 2;
    private static final int COL_CATEGORY = 3;
    private static final int COL_DESCRIPTION = 4;
    private static final int COL_INCOME_DATE = 5;
    private static final int COLUMN_COUNT = 6;

    public List<ParsedRow> parse(InputStream in) {
        List<ParsedRow> rows = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, COLUMN_COUNT)) continue;

                int rowNumber = r + 1;
                try {
                    IncomeRequest request = mapRow(row);
                    rows.add(new ParsedRow(rowNumber, request, null));
                } catch (Exception ex) {
                    log.warn("Income Excel row {} failed to parse: {}", rowNumber, ex.getMessage());
                    rows.add(new ParsedRow(rowNumber, null, ex.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new ExcelProcessingException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private IncomeRequest mapRow(Row row) {
        IncomeRequest req = new IncomeRequest();
        req.setAmount(ExcelCellReader.readBigDecimal(row.getCell(COL_AMOUNT), "Amount"));

        String currency = ExcelCellReader.readString(row.getCell(COL_CURRENCY));
        req.setCurrency(currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase());

        req.setSource(ExcelCellReader.readString(row.getCell(COL_SOURCE)));
        req.setCategory(ExcelCellReader.readString(row.getCell(COL_CATEGORY)));
        req.setDescription(ExcelCellReader.readString(row.getCell(COL_DESCRIPTION)));
        req.setIncomeDate(ExcelCellReader.readDate(row.getCell(COL_INCOME_DATE), "Income date"));

        return req;
    }

    public record ParsedRow(int rowNumber, IncomeRequest request, String error) {
        public boolean isError() {
            return error != null;
        }
    }
}