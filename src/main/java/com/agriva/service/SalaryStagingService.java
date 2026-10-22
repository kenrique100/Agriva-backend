package com.agriva.service;

import com.agriva.model.dto.salary.SalarySubmissionEvent;

public interface SalaryStagingService {
    void persistStaged(SalarySubmissionEvent event);
}