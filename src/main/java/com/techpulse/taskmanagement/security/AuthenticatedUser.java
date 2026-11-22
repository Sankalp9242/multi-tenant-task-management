package com.techpulse.taskmanagement.security;

import com.techpulse.taskmanagement.enums.Role;

import java.security.Principal;

public record AuthenticatedUser(Long userId, String email, Role role, Long tenantId) implements Principal
{
    @Override
    public String getName() {
        return email;
    }
}
