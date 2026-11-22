package com.techpulse.taskmanagement.dto.response;

import com.techpulse.taskmanagement.enums.Role;

public record UserResponse(Long id, String email, Role role, Long tenantId) {
}
