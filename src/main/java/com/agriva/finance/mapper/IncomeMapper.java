package com.agriva.finance.mapper;

import com.agriva.finance.model.dto.response.IncomeResponse;
import com.agriva.finance.model.dto.response.IncomeStagingResponse;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.shared.models.ref.UserRef;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class IncomeMapper {

    public IncomeResponse toResponse(Income income, Map<UUID, UserRef> userById) {
        UserRef creator = income.getCreatedByUserId() == null
                ? null
                : userById.get(income.getCreatedByUserId());

        String username = creator != null ? creator.username() : null;
        String email = creator != null ? creator.email() : null;

        if (username == null && email == null && income.getCreatedByUserId() != null) {
            username = "deleted-user";
            email = income.getCreatedByUserId() + "@orphaned";
        }

        return new IncomeResponse(
                income.getId(),
                income.getFarmId(),
                income.getTransactionId(),
                income.getReason(),
                income.getQuantity(),
                income.getAmountReceived(),
                income.getExpectedAmount(),
                income.getDueBalance(),
                income.getReceipt(),
                income.getCurrency(),
                income.getCategory(),
                income.getIncomeDate(),
                income.getCreatedByUserId(),
                username,
                email,
                income.getCreatedAt(),
                income.getUpdatedAt()
        );
    }

    public IncomeStagingResponse toStaging(StagedIncome s) {
        return new IncomeStagingResponse(
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
                s.getMovedIncomeId(),
                s.getRetryCount()
        );
    }
}