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
            case "STOP" -> {
                if (t.getStatus() != TaskStatus.IN_PROGRESS)
                    throw new InvalidTaskStateException("Task can only be stopped when IN_PROGRESS");
            }
            case "CONTINUE" -> {
                if (t.getStatus() != TaskStatus.STOPPED)
                    throw new InvalidTaskStateException("Task can only be continued when STOPPED");
            }
            case "SUBMIT" -> {
                if (t.getStatus() != TaskStatus.IN_PROGRESS)
                    throw new InvalidTaskStateException("Task can only be submitted when IN_PROGRESS");
            }
            case "CANCEL" -> {
                if (t.getStatus() == TaskStatus.COMPLETED
                        || t.getStatus() == TaskStatus.CANCELLED
                        || t.getStatus() == TaskStatus.UNCOMPLETED)
                    throw new InvalidTaskStateException("Cannot cancel a task that is already final");
            }
            default -> throw new InvalidTaskActionException("Unknown action: " + action);
        }
    }

    public void validateCanValidate(Task t) {
        if (!Boolean.TRUE.equals(t.getIsSubmitted()) || t.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Only submitted IN_PROGRESS tasks can be validated");
        }
    }
}