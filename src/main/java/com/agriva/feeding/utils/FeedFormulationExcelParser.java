package com.agriva.feeding.utils;

import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.FormulationSubmissionPayload;
import com.agriva.shared.exception.ExcelProcessingException;
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
public class FeedFormulationExcelParser {

    private static final int HEADER_ROW = 0;

    public List<ParsedRow> parse(InputStream in) {
        DataFormatter fmt = new DataFormatter();
        LinkedHashMap<String, Group> groups = new LinkedHashMap<>();

        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(HEADER_ROW);
            if (header == null) throw new ExcelProcessingException("Excel sheet is empty");

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell c : header) {
                String h = fmt.formatCellValue(c).trim().toLowerCase();
                if (!h.isEmpty()) colMap.put(h, c.getColumnIndex());
            }

            if (!colMap.containsKey("name")
                    || !colMap.containsKey("target_species")
                    || !colMap.containsKey("mode")
                    || !colMap.containsKey("target_quantity_kg")
                    || !colMap.containsKey("ingredient_name")
                    || !colMap.containsKey("quantity_kg")) {
                throw new ExcelProcessingException(
                        "Required headers: name, target_species, mode, target_quantity_kg, "
                                + "ingredient_name, quantity_kg. Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, header.getLastCellNum())) continue;
                int rowNum = r + 1;

                try {
                    String name = ExcelCellReader.readString(getCell(row, colMap, "name"));
                    if (name == null || name.isBlank())
                        throw new ExcelProcessingException("name is required");

                    String species = ExcelCellReader.readString(getCell(row, colMap, "target_species"));
                    if (species == null || species.isBlank())
                        throw new ExcelProcessingException("target_species is required");

                    String modeStr = ExcelCellReader.readString(getCell(row, colMap, "mode"));
                    FormulationMode mode;
                    try {
                        mode = FormulationMode.valueOf(modeStr.trim().toUpperCase());
                    } catch (Exception e) {
                        throw new ExcelProcessingException("Invalid mode: " + modeStr);
                    }

                    BigDecimal targetKg = ExcelCellReader.readBigDecimal(
                            getCell(row, colMap, "target_quantity_kg"), "target_quantity_kg");

                    BigDecimal cp = readOptionalDecimal(row, colMap, "target_cp_value");

                    String ingName = ExcelCellReader.readString(getCell(row, colMap, "ingredient_name"));
                    if (ingName == null || ingName.isBlank())
                        throw new ExcelProcessingException("ingredient_name is required");

                    BigDecimal qty = ExcelCellReader.readBigDecimal(
                            getCell(row, colMap, "quantity_kg"), "quantity_kg");

                    // with the same name are NOT merged into one corrupted record.
                    String groupKey = buildGroupKey(name, species);
                    Group g = groups.computeIfAbsent(groupKey,
                            k -> new Group(name.trim(), species.trim(), mode, targetKg, cp));
                    g.lines.add(new FormulationSubmissionPayload.Line(ingName.trim(), qty));
                    g.firstRow = Math.min(g.firstRow, rowNum);

                } catch (Exception ex) {
                    log.warn("Feed-formulation Excel row {} failed: {}", rowNum, ex.getMessage());
                    String name = ExcelCellReader.readString(getCell(row, colMap, "name"));
                    String species = ExcelCellReader.readString(getCell(row, colMap, "target_species"));
                    if (name != null && !name.isBlank()) {
                        String groupKey = buildGroupKey(name, species);
                        Group g = groups.computeIfAbsent(groupKey,
                                k -> new Group(name.trim(),
                                        species == null ? null : species.trim(),
                                        null, null, null));
                        g.error = ex.getMessage();
                        g.firstRow = Math.min(g.firstRow, rowNum);
                    } else {
                        groups.put("__orphan_" + rowNum, orphan(rowNum, ex.getMessage()));
                    }
                }
            }
        } catch (ExcelProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ExcelProcessingException("Failed to parse Excel: " + e.getMessage(), e);
        }

        List<ParsedRow> out = new ArrayList<>();
        for (Group g : groups.values()) {
            if (g.error != null) {
                out.add(new ParsedRow(g.firstRow, null, g.error));
            } else if (g.mode == null || g.targetKg == null) {
                out.add(new ParsedRow(g.firstRow, null, "Missing formulation metadata"));
            } else {
                out.add(new ParsedRow(g.firstRow,
                        new FormulationSubmissionPayload(
                                g.name, g.species, g.mode, g.targetKg, g.cp, g.lines),
                        null));
            }
        }
        return out;
    }

    /**
     * Builds a composite grouping key so formulations with the same name
     * but different target species are treated as distinct.
     */
    private static String buildGroupKey(String name, String species) {
        String n = name == null ? "" : name.trim();
        String s = species == null ? "" : species.trim();
        return n + "||" + s;
    }

    private static BigDecimal readOptionalDecimal(Row row, Map<String, Integer> colMap, String key) {
        String s = ExcelCellReader.readString(getCell(row, colMap, key));
        if (s == null || s.isBlank()) return null;
        return new BigDecimal(s.replace(",", ""));
    }

    private static Group orphan(int rowNum, String error) {
        Group g = new Group("__orphan_" + rowNum, null, null, null, null);
        g.error = error;
        g.firstRow = rowNum;
        return g;
    }

    private static Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    private static class Group {
        final String name;
        final String species;
        final FormulationMode mode;
        final BigDecimal targetKg;
        final BigDecimal cp;
        final List<FormulationSubmissionPayload.Line> lines = new ArrayList<>();
        String error;
        int firstRow = Integer.MAX_VALUE;

        Group(String name, String species, FormulationMode mode, BigDecimal targetKg, BigDecimal cp) {
            this.name = name;
            this.species = species;
            this.mode = mode;
            this.targetKg = targetKg;
            this.cp = cp;
        }
    }

    public record ParsedRow(int rowNumber, FormulationSubmissionPayload payload, String error) {
        public boolean isError() { return error != null; }
    }
}