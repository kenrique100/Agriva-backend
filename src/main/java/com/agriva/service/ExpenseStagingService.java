package com.agriva.service;

import com.agriva.model.dto.expense.ExpenseSubmissionEvent;

public interface ExpenseStagingService {

    void persistStaged(ExpenseSubmissionEvent event);
}