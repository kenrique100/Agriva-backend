package com.agriva.mapper;

import com.agriva.model.dto.transaction.TransactionResponse;
import com.agriva.model.entity.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction tx, String createdByUsername) {
        return new TransactionResponse(
                tx.getId(),
                tx.getFarmId(),
                tx.getTransactionRef(),
                tx.getOccurredAt(),
                tx.getStatus().name(),
                tx.getNotes(),
                tx.getCreatedByUserId(),
                createdByUsername,
                tx.getCreatedAt(),
                tx.getUpdatedAt()
        );
    }
}