package com.techpulse.taskmanagement.dto.response;

import java.time.Instant;

public record TenantRegistrationResponse(Long tenantId, String tenantName, Instant createdAt,
                                         Long adminUserId, String adminEmail) {
}
