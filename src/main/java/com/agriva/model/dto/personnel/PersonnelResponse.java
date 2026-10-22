package com.agriva.model.dto.personnel;

import com.agriva.model.entity.enums.Department;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.enums.PersonnelStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelResponse {

    private UUID id;
    private UUID farmId;
    private UUID userId;
    private String name;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String email;
    private String nationalId;
    private String phoneNumber;
    private Department department;
    private LocalDate dateOfEmployment;
    private String profilePictureUrl;
    private String profilePictureThumbnailUrl;
    private Double totalHoursWorkedLast30Days;
    private PersonnelStatus status;
    private LocalDateTime statusChangeTimestamp;
    private LocalDateTime statusExpiration;
    private Duration suspensionDuration;
    private LocalDateTime terminationTimestamp;
    private Integer workingDaysCount;
    private Integer totalProductiveDays;
    private LocalDate currentPeriodStartDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}