package com.agriva.farming.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropBulkUploadResponse {
    private int totalRows;
    private int created;
    private int queued;
    private int failed;
    private List<RowResult> results = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowResult {
        private int rowNumber;
        private String entityId;
        private String status;
        private String message;
    }
}