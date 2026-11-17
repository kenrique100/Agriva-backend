package com.agriva.feeding.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StagedFeedingEventId implements Serializable {
    private UUID id;
    private LocalDateTime submittedAt;
}