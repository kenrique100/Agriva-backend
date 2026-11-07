package com.agriva.personnel.models.entity;

import com.agriva.personnel.models.enums.Department;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "personnel",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_personnel_user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "ux_personnel_farm_national_id",
                        columnNames = {"farm_id", "national_id"})
        }
)
@Data
@EqualsAndHashCode(callSuper = true, exclude = "statusHistory")
@ToString(exclude = "statusHistory")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Personnel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Gender gender;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 100)
    private String email;

    @Column(name = "national_id", length = 15)
    private String nationalId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Department department;

    @Column(name = "date_of_employment")
    private LocalDate dateOfEmployment;

    @Column(name = "profile_picture_path", length = 255)
    private String profilePicturePath;

    @Column(name = "profile_picture_thumbnail_path", length = 255)
    private String profilePictureThumbnailPath;

    @Column(name = "total_hours_worked_last_30_days")
    @Builder.Default
    private Double totalHoursWorkedLast30Days = 0.0;

    @Column(name = "working_days_count", nullable = false)
    @Builder.Default
    private Integer workingDaysCount = 0;

    @Column(name = "total_productive_days", nullable = false)
    @Builder.Default
    private Integer totalProductiveDays = 0;

    @Column(name = "current_period_start_date", nullable = false)
    @Builder.Default
    private LocalDate currentPeriodStartDate = LocalDate.now();

    @Column(name = "last_productivity_update")
    private LocalDate lastProductivityUpdate;

    @Column(name = "last_productivity_reset_date")
    private LocalDate lastProductivityResetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PersonnelStatus status = PersonnelStatus.ACTIVE;

    @Column(name = "status_change_timestamp")
    private LocalDateTime statusChangeTimestamp;

    @Column(name = "status_expiration")
    private LocalDateTime statusExpiration;

    @Column(name = "suspension_duration", columnDefinition = "interval")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration suspensionDuration;

    @Column(name = "termination_timestamp")
    private LocalDateTime terminationTimestamp;

    @OneToMany(mappedBy = "personnel", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PersonnelStatusHistory> statusHistory = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;
}