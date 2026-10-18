package com.agriva.mapper;

import com.agriva.model.dto.investment.InvestmentResponse;
import com.agriva.model.entity.Investment;
import com.agriva.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class InvestmentMapper {

    public InvestmentResponse toResponse(Investment inv, Map<UUID, User> userById) {
        User creator = inv.getCreatedByUserId() == null
                ? null
                : userById.get(inv.getCreatedByUserId());

        String username = creator != null ? creator.getUsername() : null;
        String email = creator != null ? creator.getEmail() : null;

        if (username == null && email == null && inv.getCreatedByUserId() != null) {
            username = "deleted-user";
            email = inv.getCreatedByUserId() + "@orphaned";
        }

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
                inv.getUpdatedAt()
        );
    }
}