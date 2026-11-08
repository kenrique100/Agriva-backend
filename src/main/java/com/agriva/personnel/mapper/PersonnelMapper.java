package com.agriva.personnel.mapper;

import com.agriva.personnel.models.dto.response.PersonnelResponse;
import com.agriva.personnel.models.dto.response.PersonnelStatusHistoryResponse;
import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.entity.PersonnelStatusHistory;
import org.springframework.stereotype.Component;

@Component
public class PersonnelMapper {

    public PersonnelResponse toResponse(Personnel p) {
        return new PersonnelResponse(
                p.getId(),
                p.getFarmId(),
                p.getUserId(),
                p.getName(),
                p.getGender(),
                p.getDateOfBirth(),
                p.getEmail(),
                p.getNationalId(),
                p.getPhoneNumber(),
                p.getDepartment(),
                p.getDateOfEmployment(),
                p.getProfilePicturePath() != null
                        ? "/api/v1/files/" + p.getProfilePicturePath() : null,
                p.getProfilePictureThumbnailPath() != null
                        ? "/api/v1/files/" + p.getProfilePictureThumbnailPath() : null,
                p.getTotalHoursWorkedLast30Days(),
                p.getStatus(),
                p.getStatusChangeTimestamp(),
                p.getStatusExpiration(),
                p.getSuspensionDuration(),
                p.getTerminationTimestamp(),
                p.getWorkingDaysCount(),
                p.getTotalProductiveDays(),
                p.getCurrentPeriodStartDate(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    public PersonnelStatusHistoryResponse toHistoryResponse(PersonnelStatusHistory h) {
        return new PersonnelStatusHistoryResponse(
                h.getStatus(),
                h.getStartTimestamp(),
                h.getEndTimestamp(),
                h.getAllocatedDuration(),
                h.getActualDuration(),
                h.getExpectedEndTimestamp()
        );
    }
}