package com.techpulse.taskmanagement.dto.request;

import com.techpulse.taskmanagement.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Email @NotNull @Size(max = 254) String email,
        @NotNull @Size(min = 8, max = 72) String password,
        @NotNull Role role) {
}
