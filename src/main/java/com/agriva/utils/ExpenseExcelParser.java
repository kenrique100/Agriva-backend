package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.expense.ExpenseRequest;
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
public class ExpenseExcelParser {

    private static final int HEADER_ROW = 0;
    private static final int COL_AMOUNT = 0;
    private static final int COL_CURRENCY = 1;
    private static final int COL_CATEGORY = 2;
    private static final int COL_VENDOR = 3;
    private static final int COL_DESCRIPTION = 4;
    private static final int COL_EXPENSE_DATE = 5;
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
                    ExpenseRequest request = mapRow(row);
                    rows.add(new ParsedRow(rowNumber, request, null));
                } catch (Exception ex) {
                    log.warn("Expense Excel row {} failed to parse: {}", rowNumber, ex.getMessage());
                    rows.add(new ParsedRow(rowNumber, null, ex.getMessage()));
                }
            }
        } catch (Exception e) {
            throw new ExcelProcessingException("Failed to parse Excel file: " + e.getMessage(), e);
        }

        return rows;
    }

    private ExpenseRequest mapRow(Row row) {
        ExpenseRequest req = new ExpenseRequest();
        req.setAmount(ExcelCellReader.readBigDecimal(row.getCell(COL_AMOUNT), "Amount"));

        String currency = ExcelCellReader.readString(row.getCell(COL_CURRENCY));
        req.setCurrency(currency == null || currency.isBlank() ? "USD" : currency.trim().toUpperCase());

        req.setCategory(ExcelCellReader.readString(row.getCell(COL_CATEGORY)));
        req.setVendor(ExcelCellReader.readString(row.getCell(COL_VENDOR)));
        req.setDescription(ExcelCellReader.readString(row.getCell(COL_DESCRIPTION)));
        req.setExpenseDate(ExcelCellReader.readDate(row.getCell(COL_EXPENSE_DATE), "Expense date"));

        return req;
    }

    public record ParsedRow(int rowNumber, ExpenseRequest request, String error) {
        public boolean isError() {
            return error != null;
        }
    }
}