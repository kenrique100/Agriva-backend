package com.agriva.utils;

import com.agriva.exception.ExcelProcessingException;
import com.agriva.model.dto.salary.SalaryPaymentRequest;
import com.agriva.model.entity.Personnel;
import com.agriva.repository.PersonnelRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
@RequiredArgsConstructor
public class SalaryExcelParser {

    private static final int HEADER_ROW = 0;

    private final PersonnelRepository personnelRepo;

    public List<ParsedRow> parse(InputStream in, UUID farmId) {
        List<ParsedRow> rows = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        // Preload email -> personnel for this farm (Live only).
        Map<String, Personnel> byEmail = new HashMap<>();
        for (Personnel p : personnelRepo.findByFarmIdLive(farmId, Pageable.unpaged())
                .getContent()) {
            if (p.getEmail() != null) byEmail.put(p.getEmail().toLowerCase(), p);
        }

        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(HEADER_ROW);
            if (header == null) throw new ExcelProcessingException("Excel sheet is empty");

            Map<String, Integer> colMap = new HashMap<>();
            for (Cell c : header) {
                String h = fmt.formatCellValue(c).trim().toLowerCase();
                if (!h.isEmpty()) colMap.put(h, c.getColumnIndex());
            }
            if (!colMap.containsKey("personnel_email")
                    || !colMap.containsKey("amount")
                    || !colMap.containsKey("payment_date")) {
                throw new ExcelProcessingException(
                        "Required headers: personnel_email, amount, payment_date. Found: "
                                + colMap.keySet());
            }

            for (int r = HEADER_ROW + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || ExcelCellReader.isEmptyRow(row, header.getLastCellNum()))
                    continue;
                int rowNum = r + 1;
                try {
                    SalaryPaymentRequest req = new SalaryPaymentRequest();
                    String email = ExcelCellReader.readString(
                            getCell(row, colMap, "personnel_email"));
                    if (email == null || email.isBlank())
                        throw new ExcelProcessingException("personnel_email is required");
                    Personnel p = byEmail.get(email.trim().toLowerCase());
                    if (p == null)
                        throw new ExcelProcessingException("Personnel not found: " + email);
                    req.setPersonnelId(p.getId());
                    req.setAmount(ExcelCellReader.readBigDecimal(
                            getCell(row, colMap, "amount"), "Amount"));
                    req.setPaymentDate(ExcelCellReader.readDate(
                            getCell(row, colMap, "payment_date"), "Payment date"));
                    rows.add(new ParsedRow(rowNum, req, null));
                } catch (Exception ex) {
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

    private Cell getCell(Row row, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        return idx == null ? null : row.getCell(idx);
    }

    public record ParsedRow(int rowNumber, SalaryPaymentRequest request, String error) {
        public boolean isError() { return error != null; }
    }
}