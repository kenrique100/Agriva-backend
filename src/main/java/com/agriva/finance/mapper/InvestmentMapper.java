package com.agriva.finance.mapper;

import com.agriva.finance.model.dto.response.InvestmentResponse;
import com.agriva.finance.model.entity.Investment;
import com.agriva.identity.models.entity.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class InvestmentMapper {

    public InvestmentResponse toResponse(Investment inv, Map<UUID, User> userById) {
        return toResponse(inv, userById, BigDecimal.ZERO);
    }

    public InvestmentResponse toResponse(Investment inv, Map<UUID, User> userById, BigDecimal spentAmount) {
        User creator = inv.getCreatedByUserId() == null
                ? null
                : userById.get(inv.getCreatedByUserId());

        String username = creator != null ? creator.getUsername() : null;
        String email = creator != null ? creator.getEmail() : null;

        if (username == null && email == null && inv.getCreatedByUserId() != null) {
            username = "deleted-user";
            email = inv.getCreatedByUserId() + "@orphaned";
        }

        BigDecimal spent = spentAmount == null ? BigDecimal.ZERO : spentAmount;
        BigDecimal remaining = inv.getAmount() == null
                ? BigDecimal.ZERO
                : inv.getAmount().subtract(spent);

        return new InvestmentResponse(
                inv.getId(),
                inv.getFarmId(),
                inv.getTransactionId(),
                inv.getAmount(),
                inv.getCurrency(),
                inv.getInvestorName(),
                inv.getType(),
                inv.getDescription(),
                inv.getInvestmentDate(),
                inv.getCreatedByUserId(),
                username,
                email,
                inv.getCreatedAt(),
                inv.getUpdatedAt(),
                inv.isActive(),
                inv.getActivatedAt(),
                spent,
                remaining
        );
    }
}