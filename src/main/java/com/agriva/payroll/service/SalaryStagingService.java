package com.agriva.payroll.service;

import com.agriva.payroll.model.dto.requests.SalarySubmissionEvent;

public interface SalaryStagingService {
    void persistStaged(SalarySubmissionEvent event);
}