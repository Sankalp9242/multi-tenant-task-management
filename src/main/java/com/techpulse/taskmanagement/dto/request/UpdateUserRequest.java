package com.techpulse.taskmanagement.dto.request;

import com.techpulse.taskmanagement.enums.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email @Size(max = 254) String email,
        @Size(min = 8, max = 72) String password,
        Role role) {

    @AssertTrue(message = "Provide at least one non-empty field to update")
    public boolean hasChanges() {
        return (email != null && !email.isBlank())
                || (password != null && !password.isBlank())
                || role != null;
    }
}
