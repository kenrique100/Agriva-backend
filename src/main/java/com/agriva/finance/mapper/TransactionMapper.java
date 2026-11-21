package com.agriva.finance.mapper;

import com.agriva.finance.model.dto.response.TransactionResponse;
import com.agriva.finance.model.entity.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction tx, String createdByUsername, String createdByRole,
                                          BigDecimal totalAmount, String primaryType) {
        return new TransactionResponse(
                tx.getId(),
                tx.getFarmId(),
                tx.getTransactionRef(),
                tx.getOccurredAt(),
                tx.getStatus().name(),
                tx.getNotes(),
                tx.getCreatedByUserId(),
                createdByUsername,
                createdByRole,
                tx.getCreatedAt(),
                tx.getUpdatedAt(),
                totalAmount,
                primaryType
        );
    }
}