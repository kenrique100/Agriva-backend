package com.agriva.payroll.utils;

import com.agriva.shared.exception.BusinessException;
import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import org.springframework.stereotype.Component;

@Component
public class SalaryValidator {

    public void validate(Personnel p) {
        if (p.getStatus() == PersonnelStatus.TERMINATED) {
            throw new BusinessException("Cannot pay salary to terminated personnel");
        }
        if (p.getStatus() == PersonnelStatus.SUSPENDED) {
            throw new BusinessException("Cannot pay salary to suspended personnel");
        }
    }
}