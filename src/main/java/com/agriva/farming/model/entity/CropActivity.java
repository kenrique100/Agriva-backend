package com.agriva.farming.model.entity;

import com.agriva.farming.model.enums.ActivityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "crop_activities") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CropActivity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "crop_cycle_id", nullable = false)
    private CropCycle cropCycle;

    @Column(name = "logged_by_user_id")
    private UUID loggedByUserId;

    @Enumerated(EnumType.STRING) @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType activityType;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "linked_expense_id")
    private UUID linkedExpenseId;

    @Column(name = "linked_task_id")
    private UUID linkedTaskId;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}