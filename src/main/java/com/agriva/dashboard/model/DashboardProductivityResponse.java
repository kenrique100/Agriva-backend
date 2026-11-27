package com.agriva.dashboard.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardProductivityResponse {

    @Builder.Default
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<UserProductivity> users = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserProductivity {
        private UUID personnelId;
        private String name;
        private String department;
        private Double totalHoursWorked;
        private Double expectedHours;
        private Integer workingDays;
        private Double productivityPercentage;
    }
}