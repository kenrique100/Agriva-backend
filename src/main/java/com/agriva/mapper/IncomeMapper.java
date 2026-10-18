package com.agriva.mapper;

import com.agriva.model.dto.income.IncomeResponse;
import com.agriva.model.dto.income.IncomeStagingResponse;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class IncomeMapper {

    public IncomeResponse toResponse(Income income, Map<UUID, User> userById) {
        User creator = income.getCreatedByUserId() == null
                ? null
                : userById.get(income.getCreatedByUserId());

        String username = creator != null ? creator.getUsername() : null;
        String email = creator != null ? creator.getEmail() : null;

        if (username == null && email == null && income.getCreatedByUserId() != null) {
            username = "deleted-user";
            email = income.getCreatedByUserId() + "@orphaned";
        }

        return new IncomeResponse(
                income.getId(),
                income.getFarmId(),
                income.getTransactionId(),
                income.getAmount(),
                income.getCurrency(),
                income.getSource(),
                income.getCategory(),
                income.getDescription(),
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