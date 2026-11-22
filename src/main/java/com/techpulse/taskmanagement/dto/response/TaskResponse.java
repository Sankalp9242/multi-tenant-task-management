package com.techpulse.taskmanagement.dto.response;

import com.techpulse.taskmanagement.enums.TaskStatus;

public record TaskResponse(Long id, String title, String description, TaskStatus status,
                           Long assignedToId, String assignedToEmail, Long tenantId) {
}
