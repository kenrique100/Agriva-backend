package com.agriva.model.dto.personnel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelProfileResponse {
    private PersonnelResponse personnel;
    private List<PersonnelStatusHistoryResponse> statusHistory;
    private List<com.agriva.model.dto.task.TaskResponse> tasks;
    private List<com.agriva.model.dto.salary.SalaryPaymentResponse> salaryPayments;
}