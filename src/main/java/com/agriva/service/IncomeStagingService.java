package com.agriva.service;

import com.agriva.model.dto.income.IncomeSubmissionEvent;

public interface IncomeStagingService {

    void persistStaged(IncomeSubmissionEvent event);
}