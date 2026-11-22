package com.techpulse.taskmanagement.dto.request;

import com.techpulse.taskmanagement.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTaskStatusRequest(@NotNull TaskStatus status) {
}
