package com.agriva.finance.service;

import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;

public interface ExpenseStagingService {

    void persistStaged(ExpenseSubmissionEvent event);
}