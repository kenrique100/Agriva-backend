package com.agriva.personnel.models.dto.response;

import com.agriva.payroll.model.dto.response.SalaryPaymentResponse;
import com.agriva.task.model.dto.response.TaskResponse;
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
    private List<TaskResponse> tasks;
    private List<SalaryPaymentResponse> salaryPayments;
}