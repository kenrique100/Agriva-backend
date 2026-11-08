package com.agriva.task.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductivityStatsResponse {
    private Double totalHoursWorked;
    private Double dailyAverage;
    private Integer workingDays;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private Double productivityPercentage;
}