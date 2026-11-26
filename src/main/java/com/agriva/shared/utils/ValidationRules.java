package com.agriva.shared.utils;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.exception.PersonnelNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ValidationRules {

    private static final int NOTES_MAX_LEN = 500;
    private static final int TITLE_MAX_LEN = 100;
    private static final int DESCRIPTION_MAX_LEN = 500;

    private ValidationRules() {}

    public static Personnel assertTaskAssignable(
            PersonnelRepository personnelRepo,
            TaskOpenTaskChecker openTaskChecker,
            java.util.UUID personnelId,
            java.util.UUID farmId,
            java.util.UUID excludeTaskId,
            String title,
            String description,
            LocalDate deadline,
            Integer expectedHours) {

        if (personnelId == null) throw new IllegalArgumentException("Personnel is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Title is required");
        if (title.length() > TITLE_MAX_LEN)
            throw new IllegalArgumentException("Title must be at most " + TITLE_MAX_LEN + " characters");
        if (description != null && description.length() > DESCRIPTION_MAX_LEN)
            throw new IllegalArgumentException("Description must be at most " + DESCRIPTION_MAX_LEN + " characters");
        if (deadline == null) throw new IllegalArgumentException("Deadline is required");
        if (deadline.isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Deadline cannot be in the past");
        if (expectedHours == null || expectedHours <= 0)
            throw new IllegalArgumentException("Expected hours must be positive");

        Personnel p = personnelRepo.findByIdAndFarmIdLive(personnelId, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + personnelId));

        if (Boolean.TRUE.equals(p.getIsFarmOwner())) {
            throw new IllegalArgumentException("The farm owner cannot be assigned tasks.");
        }
        if (p.getStatus() != PersonnelStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "Cannot assign tasks to personnel who are not ACTIVE (current: "
                            + p.getStatus() + ").");
        }

        openTaskChecker.assertNoOpenTask(personnelId, excludeTaskId, p.getName());
        return p;
    }

    public static String taskAssignableError(
            PersonnelRepository personnelRepo,
            TaskOpenTaskChecker openTaskChecker,
            java.util.UUID personnelId,
            java.util.UUID farmId,
            java.util.UUID excludeTaskId,
            String title,
            String description,
            LocalDate deadline,
            Integer expectedHours) {
        try {
            assertTaskAssignable(personnelRepo, openTaskChecker, personnelId, farmId,
                    excludeTaskId, title, description, deadline, expectedHours);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    public static Personnel assertSalaryPayable(
            PersonnelRepository personnelRepo,
            java.util.UUID personnelId,
            java.util.UUID farmId,
            BigDecimal amount,
            LocalDate paymentDate,
            String notes) {

        if (personnelId == null) throw new IllegalArgumentException("Personnel is required");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Amount must be positive");
        if (paymentDate == null) throw new IllegalArgumentException("Payment date is required");
        if (paymentDate.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("Payment date cannot be in the future");
        if (notes != null && notes.length() > NOTES_MAX_LEN)
            throw new IllegalArgumentException("Notes must be at most " + NOTES_MAX_LEN + " characters");

        Personnel p = personnelRepo.findByIdAndFarmIdLive(personnelId, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + personnelId));

        if (Boolean.TRUE.equals(p.getIsFarmOwner())) {
            throw new IllegalArgumentException("The farm owner cannot be paid a salary here.");
        }
        if (p.getStatus() == PersonnelStatus.TERMINATED) {
            throw new IllegalArgumentException("Cannot pay salary to terminated personnel");
        }
        if (p.getStatus() == PersonnelStatus.SUSPENDED) {
            throw new IllegalArgumentException("Cannot pay salary to suspended personnel");
        }
        return p;
    }

    public static String salaryPayableError(
            PersonnelRepository personnelRepo,
            java.util.UUID personnelId,
            java.util.UUID farmId,
            BigDecimal amount,
            LocalDate paymentDate,
            String notes) {
        try {
            assertSalaryPayable(personnelRepo, personnelId, farmId, amount, paymentDate, notes);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    @FunctionalInterface
    public interface TaskOpenTaskChecker {
        void assertNoOpenTask(java.util.UUID personnelId,
                              java.util.UUID excludeTaskId,
                              String personnelName);
    }
}