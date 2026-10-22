package com.agriva.model.dto.income;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeSubmissionAck {
    private UUID submissionId;
    private UUID farmId;
    private String status;
    private LocalDateTime acceptedAt;
}