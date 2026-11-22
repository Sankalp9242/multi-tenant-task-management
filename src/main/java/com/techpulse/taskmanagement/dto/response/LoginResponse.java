package com.techpulse.taskmanagement.dto.response;

public record LoginResponse(String tokenType, String accessToken, long expiresInSeconds) {
}
