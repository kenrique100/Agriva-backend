package com.agriva.mapper;

import com.agriva.model.dto.salary.SalaryPaymentResponse;
import com.agriva.model.entity.SalaryPayment;
import org.springframework.stereotype.Component;

@Component
public class SalaryMapper {

    public SalaryPaymentResponse toResponse(SalaryPayment p) {
        return new SalaryPaymentResponse(
                p.getId(),
                p.getFarmId(),
                p.getPersonnel() != null ? p.getPersonnel().getId() : null,
                p.getPersonnel() != null ? p.getPersonnel().getName() : null,
                p.getAmount(),
                p.getPaymentDate(),
                p.getStatus(),
                p.getPaymentReference(),
                p.getExpenseId(),
                p.getPaidByUserId(),
                p.getCreatedAt()
        );
    }
}