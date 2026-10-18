package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class ExcelCellReader {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExcelCellReader() {
    }

    public static String readString(Cell cell) {
        if (cell == null) return null;

        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().format(DATE_FORMATTER)
                        : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> cell.getCellFormula();
                default -> null;
            };
        } catch (Exception e) {
            throw new ExcelProcessingException("Error reading string cell: " + e.getMessage(), e);
        }
    }

    public static BigDecimal readBigDecimal(Cell cell, String field) {
        if (cell == null) {
            throw new ExcelProcessingException(field + " is required");
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                return new BigDecimal(cell.getStringCellValue().trim());
            }
            throw new ExcelProcessingException(field + " must be a valid number");
        } catch (NumberFormatException e) {
            throw new ExcelProcessingException(field + " must be a valid number: " + e.getMessage());
        }
    }

    public static LocalDate readDate(Cell cell, String field) {
        if (cell == null) {
            throw new ExcelProcessingException(field + " is required");
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                return LocalDate.parse(cell.getStringCellValue().trim(), DATE_FORMATTER);
            }
            throw new ExcelProcessingException(field + " must be a valid date (yyyy-MM-dd)");
        } catch (Exception e) {
            throw new ExcelProcessingException(
                    field + " must be a valid date (yyyy-MM-dd): " + e.getMessage(), e);
        }
    }

    public static boolean isEmptyRow(Row row, int columnCount) {
        for (int c = 0; c < columnCount; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = readString(cell);
                if (value != null && !value.isBlank()) return false;
            }
        }
        return true;
    }
}