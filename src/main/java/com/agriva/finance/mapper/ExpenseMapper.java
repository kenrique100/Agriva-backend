package com.agriva.finance.mapper;

import com.agriva.finance.model.dto.response.ExpenseResponse;
import com.agriva.finance.model.dto.response.ExpenseStagingResponse;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.identity.models.entity.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(Expense expense, Map<UUID, User> userById) {
        User creator = expense.getCreatedByUserId() == null
                ? null
                : userById.get(expense.getCreatedByUserId());

        String username = creator != null ? creator.getUsername() : null;
        String email = creator != null ? creator.getEmail() : null;

        if (username == null && email == null && expense.getCreatedByUserId() != null) {
            username = "deleted-user";
            email = expense.getCreatedByUserId() + "@orphaned";
        }

        return new ExpenseResponse(
                expense.getId(),
                expense.getFarmId(),
                expense.getTransactionId(),
                expense.getReason(),
                expense.getQtyPurchased(),
                expense.getAmountPaid(),
                expense.getExpectedAmount(),
                expense.getDueBalance(),
                expense.getReceipt(),
                expense.getCurrency(),
                expense.getCategory(),
                expense.getExpenseDate(),
                expense.getCreatedByUserId(),
                username,
                email,
                expense.getCreatedAt(),
                expense.getUpdatedAt(),
                expense.getInvestmentId()
        );
    }

    public ExpenseStagingResponse toStaging(StagedExpense s) {
        return new ExpenseStagingResponse(
                s.getId(),
                s.getFarmId(),
                s.getStatus().name(),
                s.getValidationErrors(),
                s.getPayloadJson(),
                s.getSubmittedByUserId(),
                s.getSubmittedByEmail(),
                s.getSubmittedAt(),
                s.getLastAttemptAt(),
                s.getNextRetryAt(),
                s.getProcessedAt(),
                s.getMovedExpenseId(),
                s.getRetryCount()
        );
    }
}