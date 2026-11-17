package com.agriva.finance.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchStatusResponse {

    private UUID batchId;
    private int total;
    private int pending;
    private int moved;
    private int failed;
    private List<FailedRow> failedRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FailedRow {
        private Integer rowNumber;
        private UUID submissionId;
        private String errors;
    }
}