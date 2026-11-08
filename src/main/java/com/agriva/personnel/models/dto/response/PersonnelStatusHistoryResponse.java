package com.agriva.personnel.models.dto.response;

import com.agriva.personnel.models.enums.PersonnelStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelStatusHistoryResponse {
    private PersonnelStatus status;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private Duration allocatedDuration;
    private Duration actualDuration;
    private LocalDateTime expectedEndTimestamp;
}