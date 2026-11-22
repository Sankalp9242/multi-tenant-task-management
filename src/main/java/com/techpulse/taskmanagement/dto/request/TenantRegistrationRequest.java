package com.techpulse.taskmanagement.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TenantRegistrationRequest(
        @NotBlank @Size(max = 120) String tenantName,
        @NotNull @Valid AdminRegistration admin) {
    public record AdminRegistration(
            @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
}
