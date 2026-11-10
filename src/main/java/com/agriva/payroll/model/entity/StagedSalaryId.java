package com.agriva.payroll.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StagedSalaryId implements Serializable {
    private UUID id;
    private LocalDateTime submittedAt;
}