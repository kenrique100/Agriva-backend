package com.agriva.payroll.mapper;

import com.agriva.payroll.model.dto.response.SalaryPaymentResponse;
import com.agriva.payroll.model.entity.SalaryPayment;
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