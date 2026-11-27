package com.agriva.task.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    STOPPED,
    SUBMITTED,
    COMPLETED,
    UNCOMPLETED,
    CANCELLED;

    public static Set<TaskStatus> openStatuses() {
        return EnumSet.of(PENDING, IN_PROGRESS, PAUSED, STOPPED);
    }
}