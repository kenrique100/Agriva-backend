package com.agriva.utils;

import com.agriva.exception.BusinessException;
import com.agriva.model.entity.Personnel;
import com.agriva.model.entity.enums.PersonnelStatus;
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