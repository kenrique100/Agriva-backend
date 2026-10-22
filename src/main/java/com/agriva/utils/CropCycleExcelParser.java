package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.crop.CropCycleRequest;
import com.agriva.model.entity.Crop;
import com.agriva.model.entity.Field;
import com.agriva.repository.CropRepository;
import com.agriva.repository.FieldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class CropCycleExcelParser {

    private static final int HEADER_ROW = 0;

    private final FieldRepository fieldRepo;
    private final CropRepository cropRepo;

    public List<ParsedRow> parse(InputStream in, UUID farmId) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();

        // Preload fields and crops for this farm, indexed by lowercase name
        Map<String, Field> fieldByName = new HashMap<>();
        for (Field f : fieldRepo.findByFarmId(farmId)) {
            fieldByName.put(f.getName().toLowerCase(), f);
        }

        Map<String, List<Crop>> cropByName = new HashMap<>();
        for (Crop c : cropRepo.findByFarmId(farmId)) {
            cropByName.computeIfAbsent(c.getName().toLowerCase(), k -> new ArrayList<>()).add(c);
        }

        try (Workbook workbook = new XSSFWorkbook(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(HEADER_ROW);
            if (headerRow == null) throw new ExcelProcessingException("Excel sheet is empty");

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell).trim().toLowerCase();
                if (!header.isEmpty()) colMap.put(header, cell.getColumnIndex());
            }

            if (!colMap.containsKey("field_name")
                    || !colMap.containsKey("crop_name")
                    || !colMap.containsKey("planting_date")) {
                throw new ExcelProcessingException(
                        "Required headers: field_name, crop_name, planting_date. Found: " + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, headerRow.getLastCellNum())) continue;

                int rowNumber = r + 1;
                try {
                    CropCycleRequest req = new CropCycleRequest();

                    String fieldName = ExcelCellReader.readString(getCell(row, colMap, "field_name"));
                    if (fieldName == null || fieldName.isBlank())
                        throw new ExcelProcessingException("field_name is required");
                    Field field = fieldByName.get(fieldName.trim().toLowerCase());
                    if (field == null)
                        throw new ExcelProcessingException("Field not found on this farm: " + fieldName);
                    req.setFieldId(field.getId());

                    String cropName = ExcelCellReader.readString(getCell(row, colMap, "crop_name"));
                    if (cropName == null || cropName.isBlank())
                        throw new ExcelProcessingException("crop_name is required");

                    String varietyStr = ExcelCellReader.readString(getCell(row, colMap, "variety"));
                    List<Crop> matches = cropByName.getOrDefault(cropName.trim().toLowerCase(), List.of());
                    Crop crop;
                    if (matches.isEmpty()) {
                        throw new ExcelProcessingException("Crop not found in catalog: " + cropName);
                    } else if (matches.size() == 1 || varietyStr == null || varietyStr.isBlank()) {
                        crop = matches.get(0);
                    } else {
                        String v = varietyStr.trim().toLowerCase();
                        crop = matches.stream()
                                .filter(c -> c.getVariety() != null && c.getVariety().toLowerCase().equals(v))
                                .findFirst()
                                .orElseThrow(() -> new ExcelProcessingException(
                                        "Crop '" + cropName + "' has no variety '" + varietyStr + "'"));
                    }
                    req.setCropId(crop.getId());

                    req.setPlantingDate(ExcelCellReader.readDate(
                            getCell(row, colMap, "planting_date"), "Planting Date"));
                    req.setNotes(ExcelCellReader.readString(getCell(row, colMap, "notes")));

                    rows.add(new ParsedRow(rowNumber, req, null));
                } catch (Exception ex) {
                    log.warn("Crop Cycle Excel row {} failed: {}", rowNumber, ex.getMessage());
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

    private Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    public record ParsedRow(int rowNumber, CropCycleRequest request, String error) {
        public boolean isError() { return error != null; }
    }
}