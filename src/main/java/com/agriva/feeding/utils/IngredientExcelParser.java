package com.agriva.feeding.utils;

import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.utils.AbstractExcelBatchParser;
import com.agriva.shared.utils.ExcelCellReader;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class IngredientExcelParser extends AbstractExcelBatchParser<IngredientSubmissionPayload> {

    @Override
    protected Class<IngredientSubmissionPayload> requestType() {
        return IngredientSubmissionPayload.class;
    }

    @Override
    protected List<String> requiredHeaders() {
        return List.of("name", "unit");
    }

    @Override
    protected IngredientSubmissionPayload mapRow(Row row, Map<String, Integer> colMap) {
        String name = ExcelCellReader.readString(getCell(row, colMap, "name"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        String unitStr = ExcelCellReader.readString(getCell(row, colMap, "unit"));
        IngredientUnit unit = parseEnum(unitStr, IngredientUnit.class, null);
        if (unit == null) {
            throw new IllegalArgumentException(
                    "Unit is required and must be one of: KG, L, EACH");
        }

        BigDecimal quantity     = readOptionalBigDecimal(row, colMap, "quantity");
        BigDecimal costPerUnit  = readOptionalBigDecimal(row, colMap, "costperunit");
        BigDecimal density      = readOptionalBigDecimal(row, colMap, "densitykgperl");

        BigDecimal cp = readOptionalBigDecimal(row, colMap, "crudeproteinpct");
        if (cp != null && (cp.signum() < 0
                || cp.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("crude protein must be between 0 and 100");
        }

        String currencyStr = ExcelCellReader.readString(getCell(row, colMap, "currency"));
        String supplier    = ExcelCellReader.readString(getCell(row, colMap, "supplier"));

        return new IngredientSubmissionPayload(
                name.trim(),
                unit,
                quantity,
                costPerUnit,
                currencyStr == null || currencyStr.isBlank() ? null : parseCurrency(currencyStr),
                supplier,
                density,
                cp
        );
    }

    private BigDecimal readOptionalBigDecimal(Row row, Map<String, Integer> colMap, String key) {
        var cell = getCell(row, colMap, key);
        if (cell == null) return null;
        String raw = ExcelCellReader.readString(cell);
        if (raw == null || raw.isBlank()) return null;
        return ExcelCellReader.readBigDecimal(cell, key);
    }
}