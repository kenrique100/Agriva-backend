package com.agriva.utils;

import com.agriva.exception.InvalidTaskActionException;
import com.agriva.exception.InvalidTaskStateException;
import com.agriva.model.entity.Task;
import com.agriva.model.entity.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskValidator {

    public void validateAction(Task t, String action) {
        String a = action.toUpperCase();
        switch (a) {
            case "START" -> {
                if (t.getStatus() != TaskStatus.PENDING)
                    throw new InvalidTaskStateException("Task can only be started from PENDING");
            }
            case "PAUSE" -> {
                if (t.getStatus() != TaskStatus.IN_PROGRESS)
                    throw new InvalidTaskStateException("Task can only be paused when IN_PROGRESS");
            }
            case "RESUME" -> {
                if (t.getStatus() != TaskStatus.PAUSED)
                    throw new InvalidTaskStateException("Task can only be resumed when PAUSED");
            }
            case "CONTINUE" -> {
                if (t.getStatus() != TaskStatus.STOPPED && t.getStatus() != TaskStatus.PAUSED)
                    throw new InvalidTaskStateException("Task can only be continued when STOPPED or PAUSED");
            }
            case "STOP" -> {
                if (t.getStatus() != TaskStatus.IN_PROGRESS && t.getStatus() != TaskStatus.PAUSED)
                    throw new InvalidTaskStateException("Task can only be stopped when IN_PROGRESS or PAUSED");
            }
            case "SUBMIT" -> {
                if (t.getStatus() != TaskStatus.IN_PROGRESS
                        && t.getStatus() != TaskStatus.PAUSED
                        && t.getStatus() != TaskStatus.STOPPED)
                    throw new InvalidTaskStateException("Task can only be submitted when active");
                if (Boolean.TRUE.equals(t.getIsSubmitted()))
                    throw new InvalidTaskStateException("Task has already been submitted");
            }
            case "CANCEL" -> {
                if (t.getStatus() == TaskStatus.COMPLETED
                        || t.getStatus() == TaskStatus.CANCELLED
                        || t.getStatus() == TaskStatus.UNCOMPLETED
                        || t.getStatus() == TaskStatus.SUBMITTED)
                    throw new InvalidTaskStateException("Cannot cancel a task that is already final");
            }
            default -> throw new InvalidTaskActionException("Unknown action: " + action);
        }
    }

    public void validateCanValidate(Task t) {
        if (!Boolean.TRUE.equals(t.getIsSubmitted())) {
            throw new InvalidTaskStateException("Task has not been submitted for validation");
        }
        if (t.getStatus() != TaskStatus.SUBMITTED) {
            throw new InvalidTaskStateException("Task is not in SUBMITTED state");
        }
    }
}