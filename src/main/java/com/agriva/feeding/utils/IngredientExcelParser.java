package com.agriva.feeding.utils;

import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.enums.Currency;
import com.agriva.shared.utils.AbstractExcelBatchParser;
import com.agriva.shared.utils.ExcelCellReader;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
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
        String name = readFirstNonNull(row, colMap, "name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        String unitStr = readFirstNonNull(row, colMap, "unit");
        IngredientUnit unit = parseEnum(unitStr, IngredientUnit.class, null);
        if (unit == null) {
            throw new IllegalArgumentException(
                    "Unit is required and must be one of: KG, L, EACH");
        }

        BigDecimal quantity    = readOptionalBigDecimal(row, colMap, "quantity");
        BigDecimal costPerUnit = readOptionalBigDecimal(row, colMap,
                "cost_per_unit", "costperunit", "cost per unit");
        BigDecimal density     = readOptionalBigDecimal(row, colMap,
                "density_kg_per_l", "densitykgperl", "density kg per l");
        BigDecimal cp          = readOptionalBigDecimal(row, colMap,
                "crude_protein_pct", "crudeproteinpct", "crude protein pct");

        if (cp != null && (cp.signum() < 0 || cp.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("crude protein must be between 0 and 100");
        }

        String currencyStr = readFirstNonNull(row, colMap, "currency");
        String supplier    = readFirstNonNull(row, colMap, "supplier");

        Currency currency = null;
        if (currencyStr != null && !currencyStr.isBlank()) {
            try {
                currency = Currency.valueOf(currencyStr.trim().toUpperCase());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid currency: " + currencyStr);
            }
        }

        return new IngredientSubmissionPayload(
                name.trim(),
                unit,
                quantity,
                costPerUnit,
                currency,
                supplier,
                density,
                cp
        );
    }

    /**
     * Reads the first non-blank cell among the given header variants.
     * Allows the parser to accept both snake_case and compact header names.
     */
    private String readFirstNonNull(Row row, Map<String, Integer> colMap, String... keys) {
        for (String key : keys) {
            var cell = getCell(row, colMap, key);
            if (cell != null) {
                String raw = ExcelCellReader.readString(cell);
                if (raw != null && !raw.isBlank()) return raw;
            }
        }
        return null;
    }

    private BigDecimal readOptionalBigDecimal(Row row, Map<String, Integer> colMap, String... keys) {
        for (String key : keys) {
            var cell = getCell(row, colMap, key);
            if (cell != null) {
                String raw = ExcelCellReader.readString(cell);
                if (raw != null && !raw.isBlank()) {
                    try {
                        return ExcelCellReader.readBigDecimal(cell, key);
                    } catch (Exception e) {
                        throw new IllegalArgumentException(
                                "Invalid numeric value for '" + key + "': " + raw);
                    }
                }
            }
        }
        return null;
    }
}