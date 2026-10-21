package com.agriva.utils;

import com.agriva.exception.BusinessException;
import com.agriva.exception.InvalidPersonnelStatusException;
import com.agriva.model.dto.personnel.PersonnelStatusUpdateRequest;
import com.agriva.model.dto.personnel.PersonnelUpdateRequest;
import com.agriva.model.entity.Department;
import com.agriva.model.entity.Personnel;
import com.agriva.model.entity.PersonnelStatus;
import com.agriva.repository.PersonnelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;

@Component
@RequiredArgsConstructor
public class PersonnelValidator {

    private final PersonnelRepository personnelRepository;

    // validateCreation(...) removed — Personnel is no longer created
    // from a request body.

    public void validateUpdate(Personnel p, PersonnelUpdateRequest req) {
        if (req.getDateOfBirth() != null &&
                Period.between(req.getDateOfBirth(), LocalDate.now()).getYears() < 18) {
            throw new BusinessException("Personnel must be at least 18 years old");
        }
        if (req.getNationalId() != null
                && !req.getNationalId().isBlank()
                && !req.getNationalId().equals(p.getNationalId())
                && personnelRepository.existsByFarmIdAndNationalIdAndIdNot(
                p.getFarmId(), req.getNationalId(), p.getId())) {
            throw new BusinessException("National ID already exists on this farm");
        }
    }

    public void validateStatusTransition(Personnel p, PersonnelStatus newStatus) {
        if (p.getStatus() == PersonnelStatus.TERMINATED) {
            throw new InvalidPersonnelStatusException("Terminated personnel cannot be modified");
        }
        if (newStatus == PersonnelStatus.ON_LEAVE && p.getDepartment() == Department.FARM_MANAGEMENT) {
            throw new InvalidPersonnelStatusException("Farm managers cannot take leave");
        }
    }

    public void validateStatusPayload(PersonnelStatusUpdateRequest req) {
        switch (req.getStatus()) {
            case ON_LEAVE -> {
                if (req.getLeaveStartDate() == null || req.getExpectedReturnDate() == null) {
                    throw new BusinessException("Leave start and return dates are required");
                }
                if (req.getExpectedReturnDate().isBefore(req.getLeaveStartDate())) {
                    throw new BusinessException("Return date must be after start date");
                }
                if (req.getExpectedReturnDate().isBefore(LocalDate.now())) {
                    throw new BusinessException("Return date cannot be in the past");
                }
            }
            case SUSPENDED -> {
                Duration d = req.getSuspensionDuration();
                if (d == null || d.isZero() || d.isNegative()) {
                    throw new BusinessException("Suspension duration must be positive");
                }
                if (d.toDays() > 30) {
                    throw new BusinessException("Suspension cannot exceed 30 days");
                }
            }
            default -> { }
        }
    }
}