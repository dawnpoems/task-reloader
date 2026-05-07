package com.yegkim.task_reloader_api.auth.dto;

public record AccessTokenResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds
) {
}
