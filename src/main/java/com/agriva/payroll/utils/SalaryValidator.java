package com.agriva.payroll.utils;

import com.agriva.shared.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class SalaryValidator {

    public void validate(String personnelStatus) {
        if ("TERMINATED".equals(personnelStatus)) {
            throw new BusinessException("Cannot pay salary to terminated personnel");
        }
        if ("SUSPENDED".equals(personnelStatus)) {
            throw new BusinessException("Cannot pay salary to suspended personnel");
        }
    }
}