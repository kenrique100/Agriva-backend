package com.agriva.payroll.mapper;

import com.agriva.payroll.model.dto.response.SalaryPaymentResponse;
import com.agriva.payroll.model.entity.SalaryPayment;
import com.agriva.shared.port.PersonnelLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalaryMapper {

    private final PersonnelLookupPort personnelLookupPort;

    public SalaryPaymentResponse toResponse(SalaryPayment p) {
        String personnelName = p.getPersonnelId() == null ? null
                : personnelLookupPort.findById(p.getPersonnelId())
                  .map(PersonnelLookupPort.PersonnelInfo::name)
                  .orElse("Unknown");

        return new SalaryPaymentResponse(
                p.getId(),
                p.getFarmId(),
                p.getPersonnelId(),
                personnelName,
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