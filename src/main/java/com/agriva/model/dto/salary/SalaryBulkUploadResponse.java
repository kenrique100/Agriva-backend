package com.agriva.model.dto.salary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryBulkUploadResponse {
    private int totalRows;
    private int queued;
    private int failed;
    private List<RowResult> results = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        private int rowNumber;
        private UUID submissionId;
        private String status;
        private String error;
    }
}