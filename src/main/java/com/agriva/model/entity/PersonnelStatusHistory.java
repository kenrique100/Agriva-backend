package com.agriva.model.entity;

import com.agriva.model.entity.enums.PersonnelStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "personnel_status_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonnelStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personnel_id", nullable = false)
    private Personnel personnel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PersonnelStatus status;

    @Column(name = "start_timestamp", nullable = false)
    private LocalDateTime startTimestamp;

    @Column(name = "end_timestamp")
    private LocalDateTime endTimestamp;

    @Column(name = "allocated_duration", columnDefinition = "interval")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration allocatedDuration;

    @Column(name = "actual_duration", columnDefinition = "interval")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration actualDuration;

    @Column(name = "expected_end_timestamp")
    private LocalDateTime expectedEndTimestamp;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}