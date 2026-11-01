package com.agriva.model.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardProductivityResponse {
    private List<UserProductivity> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProductivity {
        private java.util.UUID personnelId;
        private String name;
        private String department;
        private Double totalHoursWorked;
        private Double expectedHours;
        private Integer workingDays;
        private Double productivityPercentage;
    }
}