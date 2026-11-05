package com.agriva.finance.service;

import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;

public interface IncomeStagingService {

    void persistStaged(IncomeSubmissionEvent event);
}