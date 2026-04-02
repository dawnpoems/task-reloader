package com.yegkim.task_reloader_api.auth.jwt;

import com.yegkim.task_reloader_api.auth.entity.UserRole;

public record AccessTokenPayload(Long userId, UserRole role) {
}
